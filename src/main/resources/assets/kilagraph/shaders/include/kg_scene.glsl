// KilaGraph scene-depth helpers. Reconstruct linear depth from a raw hardware depth sample and the
// inverse projection matrix (KG_Transforms.IProjMat, clip->view). Using the actual inverse projection
// makes this robust to the projection convention (reverse-Z, infinite far, etc.) — we never hardcode
// near/far. Assumes a GL-style NDC z range of [-1, 1] (Blaze3D targets GL clip space).

// Clip-space NDC z (-1..1) -> positive eye-space distance from the camera.
float kg_eye_from_ndcz(float ndcZ, mat4 iproj) {
    vec4 view = iproj * vec4(0.0, 0.0, ndcZ, 1.0);
    return -(view.z / view.w);
}

// Raw [0,1] hardware depth -> positive eye-space distance in world units (Unity Scene Depth "Eye").
float kg_eye_depth(float rawDepth, mat4 iproj) {
    return kg_eye_from_ndcz(rawDepth * 2.0 - 1.0, iproj);
}

// Raw [0,1] hardware depth -> normalized 0(near)..1(far) (Unity Scene Depth "Linear 01").
float kg_linear01_depth(float rawDepth, mat4 iproj) {
    float eye = kg_eye_depth(rawDepth, iproj);
    float nearD = kg_eye_from_ndcz(-1.0, iproj);
    float farD  = kg_eye_from_ndcz( 1.0, iproj);
    return (eye - nearD) / (farD - nearD);
}

// Camera plane distances (world units), for the Camera node.
float kg_camera_near(mat4 iproj) { return kg_eye_from_ndcz(-1.0, iproj); }
float kg_camera_far(mat4 iproj)  { return kg_eye_from_ndcz( 1.0, iproj); }
