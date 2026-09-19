package com.jarcraft;

import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * jarcraft alpha 1.0.0 - entry point.
 * 854x480 fixed window, 60 Hz fixed-step loop, software rendered.
 *
 * Usage:
 *   java -jar jarcraft-alpha1.0.0.jar              (play)
 *   java -jar jarcraft-alpha1.0.0.jar --selftest   (headless verification)
 */
public final class Main {
    public static final int WIN_W = 854, WIN_H = 480;
    private static final long FRAME_NANOS = 1_000_000_000L / 60L;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--selftest")) {
            int failures = SelfTest.run();
            System.out.println(failures == 0
                    ? "SELFTEST-PASS (" + failures + " failures)"
                    : "SELFTEST-FAIL (" + failures + " failures)");
            System.exit(failures == 0 ? 0 : 1);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("jarcraft needs a display. Use --selftest for headless mode.");
            System.exit(2);
            return;
        }
        new Main().run();
    }

    private void run() {
        final Frame frame = new Frame("jarcraft alpha1.0.0");
        final Canvas canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(WIN_W, WIN_H));
        frame.setResizable(false);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        final Input input = new Input();
        canvas.addKeyListener(input);
        canvas.addMouseListener(input);
        canvas.addMouseMotionListener(input);
        canvas.setFocusable(true);

        final Game game = new Game(input);
        final HUD hud = new HUD(game);
        final Robot robot;
        try {
            robot = new Robot();
        } catch (Exception ex) {
            throw new IllegalStateException("no robot", ex);
        }
        final Toolkit tk = Toolkit.getDefaultToolkit();
        final Cursor emptyCursor =
                tk.createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                        new Point(0, 0), "empty");

        frame.setVisible(true);
        canvas.createBufferStrategy(2);
        final BufferStrategy bs = canvas.getBufferStrategy();

        BufferedImage img = new BufferedImage(WIN_W, WIN_H, BufferedImage.TYPE_INT_RGB);
        final int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

        long last = System.nanoTime();
        long acc = 0;
        long fpsT0 = System.nanoTime();
        int frames = 0;

        while (true) {
            long now = System.nanoTime();
            acc += now - last;
            last = now;
            if (acc > FRAME_NANOS * 10) acc = FRAME_NANOS * 10;

            while (acc >= FRAME_NANOS) {
                acc -= FRAME_NANOS;

                // grab / release mouse
                if (!game.grabbed) {
                    if (input.clicked) {
                        game.grabbed = true;
                        canvas.setCursor(emptyCursor);
                        input.resetMouseOrigin();
                        centerMouse(robot, canvas);
                    }
                } else if (input.down(java.awt.event.KeyEvent.VK_ESCAPE)) {
                    game.grabbed = false;
                    canvas.setCursor(Cursor.getDefaultCursor());
                }
                input.consumeClick();

                game.update();
            }

            game.renderFrame(px, WIN_W, WIN_H);
            Graphics g = bs.getDrawGraphics();
            g.drawImage(img, 0, 0, null);
            hud.draw((java.awt.Graphics2D) g, WIN_W, WIN_H);
            g.dispose();
            bs.show();

            if (game.grabbed) centerMouse(robot, canvas);

            frames++;
            if (System.nanoTime() - fpsT0 >= 1_000_000_000L) {
                game.fps = frames;
                System.out.println("[jarcraft] fps=" + frames);
                frames = 0;
                fpsT0 = System.nanoTime();
            }
        }
    }

    private void centerMouse(Robot robot, Canvas canvas) {
        java.awt.Point p = canvas.getLocationOnScreen();
        robot.mouseMove(p.x + WIN_W / 2, p.y + WIN_H / 2);
    }
}
