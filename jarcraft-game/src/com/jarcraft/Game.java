package com.jarcraft;

/** Game state: world, player, ticking, block interaction. */
public final class Game {
    public static final long DAY_TICKS = 240L * 60L; // 4 minute full cycle

    public final World world = new World();
    public final Player player;
    public final Input input;
    public final Renderer renderer = new Renderer();

    public long tick;
    public int selectedSlot = 0;
    public boolean grabbed = false;
    public int fps;
    public boolean showDebug = false;

    private boolean prevLmb, prevRmb;

    public Game(Input input) {
        this.input = input;
        long t0 = System.nanoTime();
        new WorldGen(1337L).generate(world);
        long t1 = System.nanoTime();
        System.out.println("[jarcraft] world generated in " + (t1 - t0) / 1000000 + " ms");

        float[] sp = WorldGen.findSpawn(world);
        player = new Player(world, sp[0], sp[1], sp[2]);
        tick = DAY_TICKS / 3; // start in the morning
    }

    /** Advance one 60 Hz tick. */
    public void update() {
        tick++;

        // look
        if (grabbed) {
            float sens = 0.0026f;
            player.look(input.dx * sens, -input.dy * sens);
            input.consumeMouse();
        }

        // movement
        player.tick(
            input.down(java.awt.event.KeyEvent.VK_W),
            input.down(java.awt.event.KeyEvent.VK_S),
            input.down(java.awt.event.KeyEvent.VK_A),
            input.down(java.awt.event.KeyEvent.VK_D),
            input.down(java.awt.event.KeyEvent.VK_SPACE),
            input.down(java.awt.event.KeyEvent.VK_SHIFT));

        // hotbar 1-9
        for (int i = 0; i < 9; i++) {
            if (input.down(java.awt.event.KeyEvent.VK_1 + i)) selectedSlot = i;
        }

        // block interaction (edge triggered)
        boolean lmb = input.mouseDown(1);
        boolean rmb = input.mouseDown(3);
        if (grabbed) {
            if (lmb && !prevLmb) breakBlock();
            if (rmb && !prevRmb) placeBlock();
        }
        prevLmb = lmb;
        prevRmb = rmb;
    }

    /** Centre-of-screen voxel target, reach 5 blocks. */
    public float[] target() {
        return Renderer.raycast(world,
                player.eyeX(), player.eyeY(), player.eyeZ(),
                player.fwdX(), player.fwdY(), player.fwdZ(), 5f);
    }

    private void breakBlock() {
        float[] hit = target();
        if (hit == null) return;
        int bx = (int) hit[0], by = (int) hit[1], bz = (int) hit[2];
        if (Blocks.breakable(world.get(bx, by, bz))) {
            world.set(bx, by, bz, Blocks.AIR);
            world.updateSurface(bx, bz);
        }
    }

    private void placeBlock() {
        float[] hit = target();
        if (hit == null) return;
        int bx = (int) hit[3], by = (int) hit[4], bz = (int) hit[5];
        if (bx < 0 || bx >= World.SX || bz < 0 || bz >= World.SZ || by < 0 || by >= World.SY) return;
        byte cur = world.get(bx, by, bz);
        if (cur != Blocks.AIR && cur != Blocks.WATER) return;
        if (player.overlapsVoxel(bx, by, bz)) return;
        world.set(bx, by, bz, Blocks.PALETTE[selectedSlot]);
        world.updateSurface(bx, bz);
    }

    /** Current global light 0..1 from the time of day. */
    public float dayLight() {
        float t = (tick % DAY_TICKS) / (float) DAY_TICKS;
        float sun = (float) Math.sin(t * Math.PI * 2 - Math.PI * 0.5) * 0.5f + 0.5f;
        return 0.22f + 0.78f * sun;
    }

    /** Render the 3D view into an ARGB pixel buffer. */
    public void renderFrame(int[] px, int w, int h) {
        renderer.render(world, null,
                player.eyeX(), player.eyeY(), player.eyeZ(),
                player.yaw, player.pitch, px, w, h,
                dayLight(), tick);
    }
}
