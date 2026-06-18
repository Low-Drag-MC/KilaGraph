package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A single-threaded, step-able exec-flow virtual machine over a {@link GraphExecutor}. Where
 * {@link GraphExecutor#executeFrom} used to drain a queue synchronously (running whole loop bodies
 * and subgraphs inside one node's {@code execute()}), this VM holds an explicit {@link ExecFrame}
 * stack so the caller can advance one node at a time with {@link #step()} and inspect state between
 * steps — a debugger for blueprints.
 *
 * <p>It runs entirely on the calling thread (no worker thread), preserving Minecraft thread-affinity
 * for graphs that touch {@code Level}/{@code Entity}. {@code executeFrom} is now just
 * {@code begin(entry); runToCompletion();} on this VM, so the existing exec semantics and tests are
 * the regression net.</p>
 *
 * <p>Loop bodies, {@code Sequence} branches, and subgraph internals are each their own step — the
 * loop/sequence/subgraph nodes push a frame ({@link LoopFrame}/{@link SequenceFrame}/
 * {@link SubgraphFrame}) instead of draining synchronously, and {@code Break}/{@code Continue} are
 * delivered as {@link Signal}s that {@link #applySignal()} routes to the nearest {@link LoopFrame}.</p>
 *
 * <p>Not thread-safe. Drive from one thread.</p>
 */
public final class ExecSession {

    /** Lifecycle of the session. */
    public enum State { NOT_STARTED, RUNNING, FINISHED }

    /** Pending loop control raised by a Break/Continue node, consumed by {@link #applySignal()}. */
    enum Signal { NONE, BREAK, CONTINUE }

    private final GraphExecutor rootScope;
    private final Deque<ExecFrame> stack = new ArrayDeque<>();

    private NodeModel entry;
    private State state = State.NOT_STARTED;
    private Signal signal = Signal.NONE;

    @Nullable private NodeModel lastExecuted;
    private int stepCount = 0;
    private final Set<UUID> breakpoints = new HashSet<>();

    public ExecSession(GraphExecutor rootScope) {
        this.rootScope = Objects.requireNonNull(rootScope);
    }

    // ---- lifecycle --------------------------------------------------------------------------

    /** Arm the session at {@code entry}. Re-callable to restart (clears stack/counters). */
    public ExecSession begin(NodeModel entry) {
        if (entry == null) throw new IllegalArgumentException("entry is null");
        this.entry = entry;
        reset();
        return this;
    }

    /**
     * Clear all run state and re-arm at the original entry. The graph scope (pull cache, nodeState,
     * env variable store) is left untouched — call {@link GraphExecutor#clearCache()}/
     * {@link GraphExecutor#clearNodeState()} or re-seed the env yourself if you want a clean rerun.
     */
    public void reset() {
        if (entry == null) throw new IllegalStateException("begin(entry) before reset()");
        stack.clear();
        signal = Signal.NONE;
        lastExecuted = null;
        stepCount = 0;
        rootScope.reachedExecOutputs().clear();
        stack.push(ChainFrame.entry(rootScope, entry));
        state = State.RUNNING;
    }

    // ---- stepping ---------------------------------------------------------------------------

    /**
     * Execute exactly one exec node and return whether a node ran. Advances over exhausted frames
     * (running their {@link ExecFrame#resume} continuations) to find the next node; if the stack
     * empties, transitions to {@link State#FINISHED} and returns {@code false}.
     */
    public boolean step() {
        ExecFrame frame = settleToRunnableFrame();
        if (frame == null) return false;
        NodeModel node = frame.poll();
        frame.scope.executeStep(node, this, frame);
        lastExecuted = node;
        stepCount++;
        applySignal();
        return true;
    }

    /** Step up to {@code n} times; stops early if the flow finishes. Returns the number of steps taken. */
    public int step(int n) {
        int taken = 0;
        for (int i = 0; i < n; i++) {
            if (!step()) break;
            taken++;
        }
        return taken;
    }

    /** Step until the flow finishes. */
    public void runToCompletion() {
        while (step()) { /* keep stepping */ }
    }

    /**
     * Step until the flow finishes or the next node to run is a breakpoint — stopping <em>before</em>
     * the breakpoint node executes. Returns the breakpoint node it paused on, or {@code null} if the
     * flow finished without hitting one.
     */
    @Nullable
    public NodeModel runToBreakpoint() {
        while (true) {
            NodeModel next = currentNode();
            if (next != null && breakpoints.contains(next.getUid())) return next;
            if (!step()) return null;
        }
    }

    // ---- breakpoints ------------------------------------------------------------------------

    public void addBreakpoint(NodeModel node) {
        if (node != null) breakpoints.add(node.getUid());
    }

    public void removeBreakpoint(NodeModel node) {
        if (node != null) breakpoints.remove(node.getUid());
    }

    public void clearBreakpoints() {
        breakpoints.clear();
    }

    public Set<UUID> breakpoints() {
        return Set.copyOf(breakpoints);
    }

    // ---- introspection ----------------------------------------------------------------------

    public State state() {
        return state;
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    /** The next node {@link #step()} would execute, without running it. Null if finished/none ready. */
    @Nullable
    public NodeModel currentNode() {
        for (ExecFrame f : stack) {
            if (f.hasPending()) return f.peek();
        }
        return null;
    }

    /** The node the most recent {@link #step()} executed. Null before the first step. */
    @Nullable
    public NodeModel lastExecuted() {
        return lastExecuted;
    }

    public int stepCount() {
        return stepCount;
    }

    public GraphExecutor rootScope() {
        return rootScope;
    }

    /** Live view of the root environment's variable store (graph/global variables written so far). */
    public VariableStore variables() {
        return rootScope.getEnvironment().variables();
    }

    /** One entry per stack frame (innermost first): its {@link ExecFrame.Kind} and the node it would run next. */
    public record StackEntry(ExecFrame.Kind kind, @Nullable NodeModel nextNode) {}

    public List<StackEntry> callStack() {
        List<StackEntry> out = new ArrayList<>(stack.size());
        for (ExecFrame f : stack) {
            out.add(new StackEntry(f.kind(), f.peek()));
        }
        return out;
    }

    // ---- internal: used by ExecContext / frames ---------------------------------------------

    void push(ExecFrame frame) {
        stack.push(frame);
    }

    void signalBreak() {
        signal = Signal.BREAK;
    }

    void signalContinue() {
        signal = Signal.CONTINUE;
    }

    /**
     * Settle the stack so the top frame has a node ready to run, executing {@link ExecFrame#resume}
     * continuations on exhausted frames. Returns the runnable top frame, or {@code null} (and marks
     * the session {@link State#FINISHED}) when the stack drains.
     */
    @Nullable
    private ExecFrame settleToRunnableFrame() {
        if (state == State.NOT_STARTED) throw new IllegalStateException("begin(entry) before step()");
        if (state == State.FINISHED) return null;
        while (true) {
            if (stack.isEmpty()) {
                state = State.FINISHED;
                return null;
            }
            ExecFrame top = stack.peek();
            if (top.hasPending()) return top;
            if (!top.resume(this)) {
                stack.pop();
            }
            // else: frame re-armed (or advanced its cursor) — loop and re-check
        }
    }

    /**
     * Apply a pending {@code Break}/{@code Continue}: discard sub-frames down to the nearest
     * {@link LoopFrame}, clear that loop's in-flight body, and either let it re-iterate (CONTINUE)
     * or complete (BREAK). With no enclosing loop the signal escapes — mirroring the old behavior
     * where {@code BreakException}/{@code ContinueException} propagated out of {@code executeFrom}.
     */
    private void applySignal() {
        if (signal == Signal.NONE) return;
        Signal s = signal;
        signal = Signal.NONE;
        while (!stack.isEmpty()) {
            ExecFrame top = stack.peek();
            if (top instanceof LoopFrame loop) {
                loop.clearPending();               // abandon the rest of this iteration's body
                if (s == Signal.BREAK) loop.markBreak();
                return;                            // leave the loop; its next resume() decides
            }
            stack.pop();                           // discard an abandoned sub-frame
        }
        throw new IllegalStateException(
                (s == Signal.BREAK ? "Break" : "Continue") + " executed outside any loop");
    }

    /** Convenience snapshot of the env variables (name → value) for callers that want an immutable copy. */
    public Map<String, Object> variableSnapshot() {
        return variables().snapshot();
    }
}
