// Living World: the cloud field.
// One description of where the clouds are, shared by the atmosphere pass (which marches through them
// and lays their shadows on the ground) and by water and puddles (which mirror them), so a cloud, its
// shadow and its reflection always belong together.
//
// noise is the mod's tiling 256x256 texture: R = smooth, G = fbm, B = cellular (bright at cell centres),
// A = blue noise. xz is a position in blocks in the cloud layer's own frame: relative to the camera, plus
// the "cloud origin" the CPU supplies (camera position and wind travel, wrapped to 6400 blocks). Every
// scale below divides 6400, so that wrap is invisible.

// How thick the cloud layer is above a point, before coverage is applied. Roughly 0.2..0.8, median 0.5.
float lwCloudShape(sampler2D noise, vec2 xz) {
    // Whole regions of sky are cloudier or clearer than others...
    float region = texture(noise, xz * (1.0 / 6400.0) + vec2(0.13, 0.71)).r;
    // ...within them stand the individual clouds, a few hundred blocks across...
    float big = texture(noise, xz * (1.0 / 1600.0)).g;
    // ...with lumpy outlines.
    float mid = texture(noise, xz * (3.0 / 1600.0) + vec2(0.37, 0.11)).g;
    return big * 0.62 + mid * 0.26 + (region - 0.5) * 0.3 + 0.06;
}

// A cheaper version for light samples: the outline without the lumps.
float lwCloudShapeCoarse(sampler2D noise, vec2 xz) {
    float region = texture(noise, xz * (1.0 / 6400.0) + vec2(0.13, 0.71)).r;
    float big = texture(noise, xz * (1.0 / 1600.0)).g;
    return big * 0.82 + (region - 0.5) * 0.3 + 0.1;
}

// The shape value above which there is cloud, for a coverage of 0 (clear) .. 1 (overcast).
float lwCloudThreshold(float cover) {
    return 0.78 - 0.7 * clamp(cover, 0.0, 1.0);
}

// How much cloud a vertical look through the layer meets at a point, 0..1. For shadows and mirror images.
float lwCloudCover2D(sampler2D noise, vec2 xz, float cover) {
    if (cover <= 0.001) {
        return 0.0;
    }
    float d = (lwCloudShape(noise, xz) - lwCloudThreshold(cover)) * 4.5;
    return smoothstep(0.0, 1.0, d);
}
