package com.lowdragmc.kilagraph.rendertype.nodes.transform;

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
        ctx.useMinecraftUniform("Globals", "minecraft:globals.glsl");
        // Absolute world camera position. MC stores CameraBlockPos = floor(camPos) and
        // CameraOffset = floor(camPos) - camPos (the negative fractional part), so camPos = block - offset.
        ctx.output("Position", new ShaderExpr("(vec3(CameraBlockPos) - CameraOffset)", GlslType.VEC3));
        ctx.output("ScreenSize", new ShaderExpr("ScreenSize", GlslType.VEC2));

        // Camera forward in world space: view-space -Z transformed by the inverse view matrix (view->world).
        ShaderExpr iView = ctx.transformField("IViewMat", GlslType.MAT4);
        ctx.output("Direction", new ShaderExpr("normalize((" + iView.code() + " * vec4(0.0, 0.0, -1.0, 0.0)).xyz)", GlslType.VEC3));

        // Orthographic flag straight from the projection matrix's [3][3] (1 for ortho, 0 for perspective).
        ctx.useMinecraftUniform("Projection", "minecraft:projection.glsl");
        ctx.output("Orthographic", new ShaderExpr("ProjMat[3][3]", GlslType.FLOAT));

        ctx.output("NearPlane", ctx.cameraNear());
        ctx.output("FarPlane", ctx.cameraFar());
        ctx.output("ZBufferSign", new ShaderExpr("1.0", GlslType.FLOAT));
    }
}
