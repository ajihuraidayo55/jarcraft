package com.jarcraft;

/**
 * Pure-Java software voxel renderer.
 *
 * Hybrid strategy, per screen column:
 * 1) Ground pass: walk the horizontal ray through a precomputed height map
 *    (Comanche-style). Each sample projects to a screen row; because samples
 *    advance smoothly from near to far, the drawn strips tile CONTINUOUSLY -
 *    no gaps by construction. Water columns render as blue surfaces.
 * 2) Ray pass: a small fan of 3D rays catches vertical faces, cliffs, trees
 *    and overhangs that a height map cannot represent. Hits are textured
 *    spans; a per-pixel depth buffer resolves occlusion against the ground.
 *
 * No AWT dependency, so it also runs headless for self tests.
 */
public final class Renderer {
    public static final float FOV_DEG = 70f;
    public static final float MAX_DIST = 80f;

    private static final int SUB_RAYS = 10;
    private static final float CY_MIN = -0.42f, CY_MAX = 0.42f;

    private int W, H, focal;
    private float fwdX, fwdY, fwdZ, rgtX, rgtY, rgtZ, upX, upY, upZ;
    private float ex, ey, ez;

    // per-pixel depth (euclidean distance * 1024) for correct occlusion
    private int[] depth = new int[0];

    // reusable hit record
    public static final class Hit {
        public float dist;
        public int vx, vy, vz;   // hit voxel
        public int nx, ny, nz;   // face normal (towards viewer)
        public byte block;
    }

    private final Hit[] hits = new Hit[SUB_RAYS * 2];
    private int hitCount;

    public Renderer() {
        for (int i = 0; i < hits.length; i++) hits[i] = new Hit();
    }

    // ---------------- public API ----------------

    public void render(World world, Textures tex,
                       float ex, float ey, float ez,
                       float yaw, float pitch,
                       int[] px, int W, int H,
                       float dayLight, long tick) {
        this.ex = ex; this.ey = ey; this.ez = ez;
        this.W = W; this.H = H;
        focal = Math.round((W * 0.5f) / (float) Math.tan(Math.toRadians(FOV_DEG) * 0.5f));

        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw),   sy = (float) Math.sin(yaw);
        fwdX = sy * cp; fwdY = sp; fwdZ = cy * cp;
        rgtX = cy;      rgtY = 0;  rgtZ = -sy;
        upX = fwdY * rgtZ - fwdZ * rgtY;
        upY = fwdZ * rgtX - fwdX * rgtZ;
        upZ = fwdX * rgtY - fwdY * rgtX;

        if (depth.length < W * H) depth = new int[W * H];
        java.util.Arrays.fill(depth, Integer.MAX_VALUE);

        drawSky(px, dayLight);
        int[] fog = skyHorizonColor(dayLight, 1f);

        float hLen = (float) Math.sqrt(fwdX * fwdX + fwdZ * fwdZ);

        for (int x = 0; x < W; x++) {
            float cx = (x - W * 0.5f) / focal;
            float bx = fwdX + rgtX * cx;
            float by = fwdY;
            float bz = fwdZ + rgtZ * cx;

            // 1) continuous ground from the height map
            colX = x;
            if (hLen > 0.02f) groundPass(world, tex, px, bx, bz, fog, dayLight);

            // 2) fan of rays for side faces / trees / cliffs / overhangs
            hitCount = 0;
            for (int s = 0; s < SUB_RAYS; s++) {
                float cyOff = CY_MIN + (CY_MAX - CY_MIN) * s / (SUB_RAYS - 1);
                march(world, bx, by, bz, cyOff);
            }
            // draw near-to-far so the depth buffer rejects early
            for (int i = 0; i < hitCount; i++) {
                int best = -1;
                for (int j = 0; j < hitCount; j++) {
                    if (hits[j].dist < 0) continue;
                    if (best < 0 || hits[j].dist < hits[best].dist) best = j;
                }
                if (best < 0) break;
                Hit h = hits[best];
                h.dist = -1;
                drawFace(px, tex, h, x, dayLight, fog);
            }
        }
    }

    // ---------------- ground pass (height field strips) ----------------

    private void groundPass(World world, Textures tex, int[] px,
                            float bx, float bz, int[] fog, float light) {
        // walk the horizontal ray direction
        float rx = bx;
        float rz = bz;
        // strip out the vertical part: we walk horizontally
        float hl = (float) Math.sqrt(rx * rx + rz * rz);
        if (hl < 1e-4f) return;
        rx /= hl; rz /= hl;

        int prevRow = H; // first unfilled row from the bottom
        float d = 0.25f;
        float prevD = d;
        float prevX = ex + rx * d, prevZ = ez + rz * d;
        int steps = 0;

        while (d < MAX_DIST && prevRow > 0 && steps < 160) {
            steps++;
            float sxw = ex + rx * d;
            float szw = ez + rz * d;
            int ix = (int) Math.floor(sxw), iz = (int) Math.floor(szw);
            if (ix < 0 || ix >= World.SX || iz < 0 || iz >= World.SZ) {
                // outside the world: nothing more to draw
                break;
            }
            int hSurf = world.surfaceY(ix, iz);   // first air y above ground
            boolean water = hSurf <= WorldGen.SEA; // lake/sea surface
            float surfY = water ? WorldGen.SEA + 1f : hSurf;

            // project the surface point
            float relY = surfY - ey;
            float zc = rx * d * fwdX + relY * fwdY + rz * d * fwdZ;
            if (zc < 0.05f) { d = advance(d); continue; }
            float yc = rx * d * upX + relY * upY + rz * d * upZ;
            float syy = H * 0.5f - focal * yc / zc;
            int row = (int) Math.ceil(syy);
            if (row < 0) row = 0;

            if (row < prevRow) {
                // fill the strip [row, prevRow) sampling the ground between
                // the previous and current sample points for texture detail
                int block = world.get(ix, water ? hSurf : hSurf - 1, iz);
                if (water || block == Blocks.AIR) block = Blocks.WATER;
                int[] tiles = Blocks.TILES[block & 0xFF];
                int tile = tiles[0];
                int rowEnd = prevRow;
                int dNew = (int) (d * d * 1024f);
                for (int r = row; r < rowEnd; r++) {
                    float f = rowEnd - 1 == row ? 0f : (r - row) / (float) (rowEnd - 1 - row);
                    float pxw = prevX + (sxw - prevX) * (1f - f);
                    float pzww = prevZ + (szw - prevZ) * (1f - f);
                    float u = pxw - (float) Math.floor(pxw);
                    float v = pzww - (float) Math.floor(pzww);
                    int tx = (int) (u * Textures.TILE) & (Textures.TILE - 1);
                    int ty = (int) (v * Textures.TILE) & (Textures.TILE - 1);
                    int argb = Textures.texel(tile, tx, ty);
                    int rgb = Textures.shade(argb, light);
                    float dist = d; // approximate strip distance
                    float fogF = dist / 78f;
                    if (fogF > 1) fogF = 1;
                    if (fogF < 0) fogF = 0;
                    int r8 = (rgb >> 16) & 255, g8 = (rgb >> 8) & 255, b8 = rgb & 255;
                    r8 += (int) ((fog[0] - r8) * fogF);
                    g8 += (int) ((fog[1] - g8) * fogF);
                    b8 += (int) ((fog[2] - b8) * fogF);
                    int di = r * W + colX;
                    if (dNew < depth[di]) {
                        depth[di] = dNew;
                        px[di] = 0xFF000000 | (r8 << 16) | (g8 << 8) | b8;
                    }
                }
                prevRow = row;
            }
            prevD = d;
            prevX = sxw; prevZ = szw;
            d = advance(d);
        }
    }

    private int colX; // current column (set before groundPass)

    private float advance(float d) {
        float s = d < 6f ? 0.18f : d * 0.055f;
        if (s > 2.2f) s = 2.2f;
        return d + s;
    }

    // ---------------- ray marching (side faces / trees / cliffs) ----------------

    /** March one sub-ray; records its first solid hit (if any). */
    private void march(World world, float bx, float by, float bz, float cyOff) {
        if (hitCount >= hits.length) return;
        float dx = bx + upX * cyOff;
        float dy = by + upY * cyOff;
        float dz = bz + upZ * cyOff;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl; dy /= dl; dz /= dl;

        float px = ex, py = ey, pz = ez;
        int pvx = (int) Math.floor(px), pvy = (int) Math.floor(py), pvz = (int) Math.floor(pz);
        int lastAx = -1, lastStep = 0;

        float t = 0;
        float step = 0.04f;
        int steps = 0;

        while (t < MAX_DIST && steps < 220) {
            steps++;
            px += dx * step; py += dy * step; pz += dz * step;
            t += step;

            int vx = (int) Math.floor(px), vy = (int) Math.floor(py), vz = (int) Math.floor(pz);
            if (vx != pvx) { lastAx = 0; lastStep = vx > pvx ? 1 : -1; }
            else if (vy != pvy) { lastAx = 1; lastStep = vy > pvy ? 1 : -1; }
            else if (vz != pvz) { lastAx = 2; lastStep = vz > pvz ? 1 : -1; }
            pvx = vx; pvy = vy; pvz = vz;

            if (vx < -8 || vx >= World.SX + 8 || vz < -8 || vz >= World.SZ + 8) {
                return; // far outside the world -> sky
            }
            byte b = world.get(vx, vy, vz);

            if (b != Blocks.AIR && b != Blocks.WATER) {
                Hit h = hits[hitCount++];
                h.block = b;
                h.vx = vx; h.vy = vy; h.vz = vz;
                h.dist = t;
                if (lastAx == 0)      { h.nx = -lastStep; h.ny = 0; h.nz = 0; }
                else if (lastAx == 1) { h.nx = 0; h.ny = -lastStep; h.nz = 0; }
                else if (lastAx == 2) { h.nx = 0; h.ny = 0; h.nz = -lastStep; }
                else {
                    float ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
                    if (ax >= ay && ax >= az) { h.nx = dx > 0 ? -1 : 1; h.ny = 0; h.nz = 0; }
                    else if (ay >= az)        { h.nx = 0; h.ny = dy > 0 ? -1 : 1; h.nz = 0; }
                    else                      { h.nx = 0; h.ny = 0; h.nz = dz > 0 ? -1 : 1; }
                }
                return; // first hit only
            }

            if (step < 1.1f) step *= 1.045f;
        }
    }

    // ---------------- face drawing (ray pass) ----------------

    private void drawFace(int[] px, Textures tex, Hit h, int col,
                          float light, int[] fog) {
        int[] tiles = Blocks.TILES[h.block & 0xFF];
        int tile;
        float shadeF;
        if (h.ny != 0) {
            tile = tiles[h.ny > 0 ? 0 : 2];
            shadeF = h.ny > 0 ? 1.0f : 0.5f;
        } else if (h.nx != 0) {
            tile = tiles[1];
            shadeF = 0.65f;
        } else {
            tile = tiles[1];
            shadeF = 0.82f;
        }

        // face plane constant and texture axes
        float planeC;
        int ua, va; // texture u/v world axes (0=x, 1=y, 2=z)
        if (h.nx != 0) {
            planeC = h.vx + (h.nx > 0 ? 1f : 0f);
            ua = 2; va = 1; // u=z, v=y
        } else if (h.ny != 0) {
            planeC = h.vy + (h.ny > 0 ? 1f : 0f);
            ua = 0; va = 2; // u=x, v=z
        } else {
            planeC = h.vz + (h.nz > 0 ? 1f : 0f);
            ua = 0; va = 1; // u=x, v=y
        }
        float pdx = h.nx != 0 ? 1 : 0;
        float pdy = h.ny != 0 ? 1 : 0;
        float pdz = h.nz != 0 ? 1 : 0;

        // project the 4 face corners to find the span in this column
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        for (int c = 0; c < 4; c++) {
            float[] corner = cornerOf(h, c);
            float rx = corner[0] - ex, ry = corner[1] - ey, rz = corner[2] - ez;
            float zc = rx * fwdX + ry * fwdY + rz * fwdZ;
            if (zc < 0.02f) return;
            float yc = rx * upX + ry * upY + rz * upZ;
            float syy = H * 0.5f - focal * yc / zc;
            if (syy < yMin) yMin = (int) Math.ceil(syy);
            if (syy > yMax) yMax = (int) Math.floor(syy);
        }
        if (yMin < 0) yMin = 0;
        if (yMax > H - 1) yMax = H - 1;

        float cx = (col - W * 0.5f) / focal;

        for (int row = yMin; row <= yMax; row++) {
            int idx = row * W + col;

            float cyv = (H * 0.5f - row) / focal;
            float rdx = fwdX + rgtX * cx + upX * cyv;
            float rdy = fwdY + rgtY * cx + upY * cyv;
            float rdz = fwdZ + rgtZ * cx + upZ * cyv;
            float da = pdx * rdx + pdy * rdy + pdz * rdz;
            if (da < 1e-6f && da > -1e-6f) continue;
            float pea = pdx != 0 ? ex : (pdy != 0 ? ey : ez);
            float tt = (planeC - pea) / da;
            if (tt < 0) continue;
            float hx2 = ex + rdx * tt, hy2 = ey + rdy * tt, hz2 = ez + rdz * tt;
            hx2 -= h.nx * 1e-4f;
            hy2 -= h.ny * 1e-4f;
            hz2 -= h.nz * 1e-4f;
            if (Math.floor(hx2) != h.vx || Math.floor(hy2) != h.vy || Math.floor(hz2) != h.vz) continue;

            float r2 = rdx * rdx + rdy * rdy + rdz * rdz;
            int dNew = (int) (tt * tt * r2 * 1024f);
            if (dNew >= depth[idx]) continue;
            depth[idx] = dNew;

            float u = ua == 0 ? hx2 : (ua == 1 ? hy2 : hz2);
            float v = va == 0 ? hx2 : (va == 1 ? hy2 : hz2);
            float uo = ua == 0 ? h.vx : (ua == 1 ? h.vy : h.vz);
            float vo = va == 0 ? h.vx : (va == 1 ? h.vy : h.vz);
            int tx = (int) ((u - uo) * Textures.TILE) & (Textures.TILE - 1);
            int ty = (int) ((v - vo) * Textures.TILE) & (Textures.TILE - 1);
            int argb = Textures.texel(tile, tx, ty);

            float dist = (float) Math.sqrt(tt * tt * r2);
            float fogF = dist / 78f;
            if (fogF > 1) fogF = 1;
            if (fogF < 0) fogF = 0;

            int rgb = Textures.shade(argb, shadeF * light);
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b2 = rgb & 255;
            r += (int) ((fog[0] - r) * fogF);
            g += (int) ((fog[1] - g) * fogF);
            b2 += (int) ((fog[2] - b2) * fogF);

            px[idx] = 0xFF000000 | (r << 16) | (g << 8) | b2;
        }
    }

    private final float[] cornerBuf = new float[3];

    private float[] cornerOf(Hit h, int c) {
        float x0 = h.vx, y0 = h.vy, z0 = h.vz;
        if (h.nx != 0) {
            cornerBuf[0] = x0 + (h.nx > 0 ? 1 : 0);
            cornerBuf[1] = y0 + ((c & 1) != 0 ? 1 : 0);
            cornerBuf[2] = z0 + ((c & 2) != 0 ? 1 : 0);
        } else if (h.ny != 0) {
            cornerBuf[0] = x0 + ((c & 1) != 0 ? 1 : 0);
            cornerBuf[1] = y0 + (h.ny > 0 ? 1 : 0);
            cornerBuf[2] = z0 + ((c & 2) != 0 ? 1 : 0);
        } else {
            cornerBuf[0] = x0 + ((c & 1) != 0 ? 1 : 0);
            cornerBuf[1] = y0 + ((c & 2) != 0 ? 1 : 0);
            cornerBuf[2] = z0 + (h.nz > 0 ? 1 : 0);
        }
        return cornerBuf;
    }

    // ---------------- sky ----------------

    private final int[] fogCol = new int[3];

    public int[] skyHorizonColor(float dayLight, float light) {
        int r = (int) (14 + (191 - 14) * dayLight);
        int g = (int) (16 + (216 - 16) * dayLight);
        int b = (int) (32 + (242 - 32) * dayLight);
        fogCol[0] = r; fogCol[1] = g; fogCol[2] = b;
        return fogCol;
    }

    private void drawSky(int[] px, float dayLight) {
        int topR = (int) (6 + (90 - 6) * dayLight);
        int topG = (int) (8 + (150 - 8) * dayLight);
        int topB = (int) (20 + (235 - 20) * dayLight);
        int[] hz = skyHorizonColor(dayLight, 1f);
        for (int y = 0; y < H; y++) {
            float f = Math.abs(y - H * 0.5f) / (H * 0.5f);
            f = 1f - f;
            f = f * f;
            int r = topR + (int) ((hz[0] - topR) * f);
            int g = topG + (int) ((hz[1] - topG) * f);
            int b = topB + (int) ((hz[2] - topB) * f);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            int base = y * W;
            for (int x = 0; x < W; x++) px[base + x] = argb;
        }
    }

    // ---------------- exact voxel raycast (interaction & debug) ----------------

    public static float[] raycast(World world, float ox, float oy, float oz,
                                  float dx, float dy, float dz, float maxDist) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return null;
        dx /= len; dy /= len; dz /= len;

        int vx = (int) Math.floor(ox), vy = (int) Math.floor(oy), vz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        float tMaxX = intBound(ox, dx), tMaxY = intBound(oy, dy), tMaxZ = intBound(oz, dz);
        float tDeltaX = dx != 0 ? Math.abs(1 / dx) : Float.MAX_VALUE;
        float tDeltaY = dy != 0 ? Math.abs(1 / dy) : Float.MAX_VALUE;
        float tDeltaZ = dz != 0 ? Math.abs(1 / dz) : Float.MAX_VALUE;

        float px = vx, py = vy, pz = vz;
        float t = 0;
        for (int i = 0; i < 512; i++) {
            if (world.solid(vx, vy, vz) && i > 0) {
                return new float[] { vx, vy, vz, px, py, pz };
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                px = vx; vx += stepX; t = tMaxX; tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                py = vy; vy += stepY; t = tMaxY; tMaxY += tDeltaY;
            } else {
                pz = vz; vz += stepZ; t = tMaxZ; tMaxZ += tDeltaZ;
            }
            if (t > maxDist) return null;
        }
        return null;
    }

    private static float intBound(float s, float ds) {
        if (ds == 0) return Float.MAX_VALUE;
        if (ds < 0) {
            float f = s - (float) Math.floor(s);
            return f == 0 ? 0 : f / -ds;
        }
        float f = (float) Math.floor(s) + 1 - s;
        return f / ds;
    }
}
