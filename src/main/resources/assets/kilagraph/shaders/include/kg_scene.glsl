// KilaGraph scene-depth helpers. Reconstruct linear depth from a raw hardware depth sample and the
// inverse projection matrix (KG_Transforms.IProjMat, clip->view). Using the actual inverse projection
// makes this robust to the projection convention (reversed-Z etc.) - we never hardcode near/far.

// Clip-space NDC z -> positive eye-space distance from the camera.
float kg_eye_from_ndcz(float ndcZ, mat4 iproj) {
    vec4 view = iproj * vec4(0.0, 0.0, ndcZ, 1.0);
    return -(view.z / view.w);
}

// Raw [0,1] hardware depth -> positive eye-space distance in world units (Unity Scene Depth "Eye").
// zRemap = (scale, bias) from window depth to clip z: (1, 0) for a [0,1] clip range, (2, -1) for GL's [-1,1].
float kg_eye_depth(float rawDepth, mat4 iproj, vec2 zRemap) {
    return kg_eye_from_ndcz(rawDepth * zRemap.x + zRemap.y, iproj);
}

// Camera plane distances (world units), for the Camera node; ordered, since the depth may be reversed.
float kg_camera_near(mat4 iproj, vec2 zRemap) { return min(kg_eye_depth(0.0, iproj, zRemap), kg_eye_depth(1.0, iproj, zRemap)); }
float kg_camera_far(mat4 iproj, vec2 zRemap)  { return max(kg_eye_depth(0.0, iproj, zRemap), kg_eye_depth(1.0, iproj, zRemap)); }

// -1 for a reversed depth buffer, else 1 (Unity's Camera "Z Buffer Sign").
float kg_zbuffer_sign(mat4 iproj, vec2 zRemap) { return kg_eye_depth(1.0, iproj, zRemap) < kg_eye_depth(0.0, iproj, zRemap) ? -1.0 : 1.0; }

// Raw [0,1] hardware depth -> normalized 0(near)..1(far) (Unity Scene Depth "Linear 01").
float kg_linear01_depth(float rawDepth, mat4 iproj, vec2 zRemap) {
    float end0 = kg_eye_depth(0.0, iproj, zRemap);
    float end1 = kg_eye_depth(1.0, iproj, zRemap);
    float nearD = min(end0, end1);
    return (kg_eye_depth(rawDepth, iproj, zRemap) - nearD) / (max(end0, end1) - nearD);
}
