package com.lowdragmc.kilagraph.rendertype.preview;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of {@link KGPreviewContent}s offered in the RenderType preview's right-click menu. Seeded with
 * the built-in quad/cube/sphere (client-safe geometry); the client-only JSON-model content registers itself
 * from the editor (so headless never loads its baked-model code). Other mods may {@link #register} their own.
 */
public final class KGPreviewContents {

    private static final Map<String, KGPreviewContent> BY_KEY = new LinkedHashMap<>();

    public static final KGPreviewContent QUAD = register(new SimpleContent("quad", "rendertypegraph.preview.quad", KGPreviewContents::buildQuad));
    public static final KGPreviewContent CUBE = register(new SimpleContent("cube", "rendertypegraph.preview.cube", KGPreviewContents::buildCube));
    public static final KGPreviewContent SPHERE = register(new SimpleContent("sphere", "rendertypegraph.preview.sphere", KGPreviewContents::buildSphere));

    private KGPreviewContents() {}

    public static KGPreviewContent register(KGPreviewContent content) {
        BY_KEY.put(content.key(), content);
        return content;
    }

    @Nullable
    public static KGPreviewContent get(String key) {
        return BY_KEY.get(key);
    }

    public static Collection<KGPreviewContent> all() {
        return Collections.unmodifiableCollection(BY_KEY.values());
    }

    /** Ensure the static initializer ran so the built-ins are present. */
    public static void bootstrap() {}

    // ---- built-in geometry -------------------------------------------------------------------

    /** A flat unit quad in the XY plane (z=0), facing +Z, uv 0..1. */
    static void buildQuad(PreviewMeshBuilder mb) {
        float[] n = {0, 0, 1};
        mb.quad(new float[]{-0.5f, -0.5f, 0}, new float[]{0.5f, -0.5f, 0},
                new float[]{0.5f, 0.5f, 0}, new float[]{-0.5f, 0.5f, 0}, n);
    }

    /** A unit cube centered at the origin, faces CCW-from-outside (so back-face cull keeps the surface). */
    static void buildCube(PreviewMeshBuilder mb) {
        float[][] c = {
                {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
                {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}
        };
        int[][] faces = {{1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {4, 0, 1, 5}, {3, 7, 6, 2}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            mb.quad(c[q[0]], c[q[1]], c[q[2]], c[q[3]], normals[f]);
        }
    }

    /** A UV sphere (radius 0.5). Lat/long quads; the top/bottom rings degenerate to triangles. */
    static void buildSphere(PreviewMeshBuilder mb) {
        int rings = 12, segments = 16;
        float r = 0.5f;
        PreviewVertex[][] grid = new PreviewVertex[rings + 1][segments + 1];
        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * i / rings;        // 0..pi (top to bottom)
            double sinPhi = Math.sin(phi), cosPhi = Math.cos(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = 2 * Math.PI * j / segments;
                float nx = (float) (sinPhi * Math.cos(theta));
                float ny = (float) cosPhi;
                float nz = (float) (sinPhi * Math.sin(theta));
                grid[i][j] = new PreviewVertex(nx * r, ny * r, nz * r,
                        (float) j / segments, (float) i / rings, nx, ny, nz);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                PreviewVertex a = grid[i][j], b = grid[i][j + 1], cc = grid[i + 1][j + 1], d = grid[i + 1][j];
                if (i == 0) {
                    mb.tri(a, cc, d);            // top cap: a≈pole
                } else if (i == rings - 1) {
                    mb.tri(a, b, cc);            // bottom cap
                } else {
                    mb.quad(a, b, cc, d);
                }
            }
        }
    }

    /** A built-in content whose geometry is a plain builder consumer (quad/cube/sphere). */
    private record SimpleContent(String key, String translationKey,
                                 Consumer<PreviewMeshBuilder> builder) implements KGPreviewContent {
        @Override
        public Component title() {
            return Component.translatable(translationKey);
        }

        @Override
        public void build(PreviewMeshBuilder mb) {
            builder.accept(mb);
        }
    }
}
