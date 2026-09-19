package com.jarcraft;

/** Player entity: AABB physics, walking, jumping, swimming. */
public final class Player {
    public static final float HALF_W = 0.3f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE = 1.62f;

    public float x, y, z;      // feet position
    public float vx, vy, vz;
    public float yaw, pitch;   // radians; yaw 0 -> +Z
    public boolean onGround;
    public boolean inWater;

    private final World world;
    private float spawnX, spawnY, spawnZ;

    public Player(World world, float sx, float sy, float sz) {
        this.world = world;
        spawnX = sx; spawnY = sy; spawnZ = sz;
        respawn();
    }

    public void respawn() {
        x = spawnX; y = spawnY; z = spawnZ;
        vx = vy = vz = 0;
        yaw = 0; pitch = 0;
        onGround = false;
    }

    public void look(float dyaw, float dpitch) {
        yaw += dyaw;
        pitch += dpitch;
        float lim = (float) (Math.PI / 2 - 0.001);
        if (pitch > lim) pitch = lim;
        if (pitch < -lim) pitch = -lim;
        while (yaw > Math.PI) yaw -= (float) (Math.PI * 2);
        while (yaw < -Math.PI) yaw += (float) (Math.PI * 2);
    }

    /** Eye position (camera origin). */
    public float eyeX() { return x; }
    public float eyeY() { return y + EYE; }
    public float eyeZ() { return z; }

    /** Forward unit vector including pitch. */
    public float fwdX() {
        return (float) (Math.sin(yaw) * Math.cos(pitch));
    }
    public float fwdY() { return (float) Math.sin(pitch); }
    public float fwdZ() {
        return (float) (Math.cos(yaw) * Math.cos(pitch));
    }

    /** One physics tick (1/60 s). */
    public void tick(boolean fwd, boolean back, boolean left, boolean right,
                     boolean jump, boolean sneak) {
        float dt = 1f / 60f;

        int feetId = world.get((int) x, (int) (y + 0.2f), (int) z);
        int headId = world.get((int) x, (int) (y + 1.5f), (int) z);
        inWater = feetId == Blocks.WATER || headId == Blocks.WATER;

        // wish direction in world space
        float wx = 0, wz = 0;
        if (fwd)  { wx += (float) Math.sin(yaw); wz += (float) Math.cos(yaw); }
        if (back) { wx -= (float) Math.sin(yaw); wz -= (float) Math.cos(yaw); }
        if (left) { wx -= (float) Math.cos(yaw); wz += (float) Math.sin(yaw); }
        if (right){ wx += (float) Math.cos(yaw); wz -= (float) Math.sin(yaw); }
        float len = (float) Math.sqrt(wx * wx + wz * wz);
        if (len > 1e-4f) { wx /= len; wz /= len; }

        float speed = sneak ? 1.4f : (inWater ? 2.2f : 4.3f);
        vx = wx * speed;
        vz = wz * speed;

        if (inWater) {
            vy -= 8f * dt;                 // reduced gravity
            if (vy < -2f) vy = -2f;        // terminal sink speed
            if (jump) vy = 3.2f;           // swim up
            vy *= 0.92f;                   // water drag
        } else {
            vy -= 28f * dt;
            if (vy < -40f) vy = -40f;
            if (jump && onGround) {
                vy = 8.6f;
                onGround = false;
            }
        }

        // move & collide axis by axis
        moveAxis(vx * dt, 0, 0);
        moveAxis(0, vy * dt, 0);
        moveAxis(0, 0, vz * dt);

        if (y < -10) respawn();

        // default spawn if none set (fallback safety)
        if (spawnY < 1) {
            spawnY = world.highestSolid((int) spawnX, (int) spawnZ) + 1;
        }
    }

    private void moveAxis(float dx, float dy, float dz) {
        float nx = x + dx, ny = y + dy, nz = z + dz;
        if (!collides(nx, ny, nz)) {
            x = nx; y = ny; z = nz;
            if (dy != 0 && dy < 0) onGround = false;
            return;
        }
        if (dy < 0) { onGround = true; vy = 0; }
        else if (dy > 0) { vy = 0; }
        // horizontal: try stepping up 1 block (auto-climb) when walking
        if (dx != 0 || dz != 0) {
            if (onGround && !collides(nx, ny + 1.05f, nz)) {
                x = nx; y = ny + 1.05f; z = nz;
            }
        }
    }

    private boolean collides(float px, float py, float pz) {
        int x0 = (int) Math.floor(px - HALF_W), x1 = (int) Math.floor(px + HALF_W);
        int y0 = (int) Math.floor(py),          y1 = (int) Math.floor(py + HEIGHT);
        int z0 = (int) Math.floor(pz - HALF_W), z1 = (int) Math.floor(pz + HALF_W);
        for (int yy = y0; yy <= y1; yy++)
            for (int zz = z0; zz <= z1; zz++)
                for (int xx = x0; xx <= x1; xx++) {
                    if (world.solid(xx, yy, zz)) return true;
                }
        return false;
    }

    /** Would the player's AABB overlap voxel (bx,by,bz)? */
    public boolean overlapsVoxel(int bx, int by, int bz) {
        return px() - HALF_W < bx + 1 && px() + HALF_W > bx
            && py() < by + 1 && py() + HEIGHT > by
            && pz() - HALF_W < bz + 1 && pz() + HALF_W > bz;
    }

    public float px() { return x; }
    public float py() { return y; }
    public float pz() { return z; }
}
