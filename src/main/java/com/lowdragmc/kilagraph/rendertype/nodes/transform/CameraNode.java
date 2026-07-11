package com.lowdragmc.kilagraph.rendertype.nodes.transform;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Camera node: the current camera's spatial + projection properties.
 * <ul>
 *   <li>{@code Position} — absolute world camera position (from {@code Globals.CameraBlockPos} + {@code CameraOffset}).</li>
 *   <li>{@code Direction} — camera forward (world space), {@code IViewMat * (0,0,-1)}.</li>
 *   <li>{@code Orthographic} — {@code 1.0} if the projection is orthographic, {@code 0.0} if perspective.</li>
 *   <li>{@code NearPlane}/{@code FarPlane} — clip plane distances in world units (reconstructed from {@code IProjMat}).</li>
 *   <li>{@code ZBufferSign} — {@code 1.0} for our pipeline (depth increases away from the camera).</li>
 *   <li>{@code ScreenSize} — viewport size in pixels.</li>
 * </ul>
 */
@NodeAttribute(name = "rt_camera", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CameraNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_camera.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("Position", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("Direction", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("Orthographic", TypeHandles.FLOAT);
        context.addOutputPort("NearPlane", TypeHandles.FLOAT);
        context.addOutputPort("FarPlane", TypeHandles.FLOAT);
        context.addOutputPort("ZBufferSign", TypeHandles.FLOAT);
        context.addOutputPort("ScreenSize", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // All camera data from KilaGraph's own KG_Transforms block (same values as Minecraft's
        // Globals/Projection, no #moj_import) — keeps camera-reading graphs injectable under a shaderpack.
        // Absolute world camera position, MC's exact precision-split form: camPos = block - offset.
        ctx.output("Position", new ShaderExpr("(vec3("
                + ctx.transformField("CameraBlockPos", GlslType.VEC3).code() + ") - "
                + ctx.transformField("CameraOffset", GlslType.VEC3).code() + ")", GlslType.VEC3));
        ctx.output("ScreenSize", ctx.screenSize());

        // Camera forward in world space: view-space -Z transformed by the inverse view matrix (view->world).
        ShaderExpr iView = ctx.transformField("IViewMat", GlslType.MAT4);
        ctx.output("Direction", new ShaderExpr("normalize((" + iView.code() + " * vec4(0.0, 0.0, -1.0, 0.0)).xyz)", GlslType.VEC3));

        // Orthographic flag straight from the projection matrix's [3][3] (1 for ortho, 0 for perspective).
        ctx.output("Orthographic", new ShaderExpr(
                ctx.transformField("ProjMat", GlslType.MAT4).code() + "[3][3]", GlslType.FLOAT));

        ctx.output("NearPlane", ctx.cameraNear());
        ctx.output("FarPlane", ctx.cameraFar());
        ctx.output("ZBufferSign", new ShaderExpr("1.0", GlslType.FLOAT));
    }
}
