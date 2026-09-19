package com.jarcraft;

import java.util.HashSet;
import java.util.Set;

/**
 * Headless verification suite. Runs world generation, physics, interaction
 * and the software renderer without any display. Exits non-zero on failure.
 */
public final class SelfTest {

    private static int failures;

    public static int run() {
        System.out.println("=== jarcraft selftest ===");

        // ---- world generation ----
        long t0 = System.nanoTime();
        World world = new World();
        new WorldGen(1337L).generate(world);
        long ms = (System.nanoTime() - t0) / 1000000L;
        System.out.println("worldgen: " + ms + " ms, water=" + world.count(Blocks.WATER)
                + " trees-logs=" + world.count(Blocks.LOG) + " coal=" + world.count(Blocks.COAL_ORE));
        check("worldgen produced ground", world.count(Blocks.STONE) > 100000);

        // ---- downward raycast hits the surface ----
        float[] spawn = WorldGen.findSpawn(world);
        int cx = (int) spawn[0], cz = (int) spawn[2];
        int topY = world.highestSolid(cx, cz);
        float[] hit = Renderer.raycast(world, cx + 0.5f, topY + 6f, cz + 0.5f, 0, -1, 0, 20f);
        check("raycast down: hit " + Blocks.NAME[world.get(cx, topY, cz) & 0xFF],
                hit != null && (int) hit[1] == topY);

        // ---- physics: fall and land ----
        Player p = new Player(world, cx + 0.5f, topY + 8f, cz + 0.5f);
        for (int i = 0; i < 600; i++) p.tick(false, false, false, false, false, false);
        System.out.printf("physics: fall+land -> y=%.2f (ground %.2f) onGround=%b inWorld=%b%n",
                p.y, (float) topY + 1f, p.onGround,
                p.x >= 0 && p.x < World.SX && p.z >= 0 && p.z < World.SZ);
        check("player landed on ground", p.onGround && Math.abs(p.y - (topY + 1)) < 1.5f);
        check("not stuck in block",
                !world.solid((int) Math.floor(p.x), (int) Math.floor(p.y + 0.1f), (int) Math.floor(p.z)));

        // ---- walking: 120 ticks forward ----
        float sx = p.x, sz = p.z;
        for (int i = 0; i < 120; i++) p.tick(true, false, false, false, false, false);
        float moved = (float) Math.sqrt((p.x - sx) * (p.x - sx) + (p.z - sz) * (p.z - sz));
        System.out.printf("walk 120 ticks, not stuck: OK  (x=%.1f z=%.1f y=%.1f moved=%.1f)%n",
                p.x, p.z, p.y, moved);
        check("walking moved the player", moved > 6f);

        // ---- place & break ----
        int py = (int) Math.floor(p.y + 4);
        world.set(cx, py, cz, Blocks.AIR);
        world.set(cx, py, cz, Blocks.COBBLE);
        boolean placed = world.get(cx, py, cz) == Blocks.COBBLE;
        world.set(cx, py, cz, Blocks.AIR);
        boolean broken = world.get(cx, py, cz) == Blocks.AIR;
        System.out.println("place/break: " + (placed && broken ? "OK" : "FAILED"));
        check("place/break works", placed && broken);

        // ---- renderer: day vs night colour diversity + brightness ----
        Renderer r = new Renderer();
        int W = 320, H = 180;
        int[] px = new int[W * H];
        float ey = topY + 2.62f;
        r.render(world, null, cx + 0.5f, ey, cz + 0.5f, 0.7f, -0.15f,
                px, W, H, 1.0f, 6000L);
        Set<Integer> daySet = new HashSet<Integer>();
        long daySum = 0;
        for (int c : px) {
            daySet.add(c & 0xFFFFFF);
            daySum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
        }
        int dayColors = daySet.size();
        long dayAvg = daySum / px.length;

        r.render(world, null, cx + 0.5f, ey, cz + 0.5f, 0.7f, -0.15f,
                px, W, H, 0.25f, 6000L);
        Set<Integer> nightSet = new HashSet<Integer>();
        long nightSum = 0;
        for (int c : px) {
            nightSet.add(c & 0xFFFFFF);
            nightSum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
        }
        int nightColors = nightSet.size();
        long nightAvg = nightSum / px.length;

        System.out.println("render: day colors=" + dayColors + " night colors=" + nightColors);
        check("renderer produced rich colors", dayColors > 150 && nightColors > 120);
        check("day brighter than night", dayAvg > nightAvg);

        // ---- textures ----
        Set<Integer> texSet = new HashSet<Integer>();
        for (int c : Textures.PIX) texSet.add(c & 0xFFFFFF);
        System.out.println("textures: " + texSet.size() + " distinct texels");
        check("textures generated", texSet.size() > 200);

        // ---- performance: one full-resolution frame ----
        int[] big = new int[854 * 480];
        long r0 = System.nanoTime();
        r.render(world, null, cx + 0.5f, ey, cz + 0.5f, 0.7f, -0.15f,
                big, 854, 480, 1.0f, 6000L);
        System.out.println("full-res render: " + (System.nanoTime() - r0) / 1000000L + " ms");

        System.out.println("=== selftest complete: " + (failures == 0 ? "all OK" : failures + " FAILURES") + " ===");
        return failures;
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) failures++;
    }

    private SelfTest() {}
}
