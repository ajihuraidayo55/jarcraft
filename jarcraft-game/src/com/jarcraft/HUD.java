package com.jarcraft;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** 2D overlay: crosshair, hotbar, debug text. */
public final class HUD {
    private final Game game;
    private final BufferedImage[] slotIcons = new BufferedImage[9];
    private final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private final Font small = new Font(Font.MONOSPACED, Font.PLAIN, 11);

    public HUD(Game game) {
        this.game = game;
        for (int i = 0; i < 9; i++) {
            slotIcons[i] = tileImage(Blocks.TILES[Blocks.PALETTE[i] & 0xFF][0]);
        }
    }

    private BufferedImage tileImage(int tile) {
        BufferedImage img = new BufferedImage(Textures.TILE, Textures.TILE,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < Textures.TILE; y++)
            for (int x = 0; x < Textures.TILE; x++) {
                int argb = Textures.texel(tile, x, y);
                img.setRGB(x, y, argb & 0xFFFFFF); // strip alpha for RGB image
            }
        return img;
    }

    public void draw(Graphics2D g, int w, int h) {
        // crosshair
        g.setColor(Color.WHITE);
        g.setXORMode(Color.DARK_GRAY);
        g.fillRect(w / 2 - 8, h / 2 - 1, 16, 2);
        g.fillRect(w / 2 - 1, h / 2 - 8, 2, 16);
        g.setPaintMode();

        // hotbar
        int slot = 22, gap = 2;
        int total = 9 * slot + 8 * gap;
        int x0 = (w - total) / 2, y0 = h - slot - 8;
        for (int i = 0; i < 9; i++) {
            int x = x0 + i * (slot + gap);
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(x, y0, slot, slot);
            g.setColor(i == game.selectedSlot ? Color.WHITE : new Color(90, 90, 90));
            g.drawRect(x, y0, slot, slot);
            g.drawImage(slotIcons[i], x + 3, y0 + 3, slot - 6, slot - 6, null);
            g.setColor(new Color(230, 230, 230));
            g.setFont(small);
            g.drawString(String.valueOf(i + 1), x + 3, y0 + slot - 3);
        }
        g.setColor(Color.WHITE);
        g.setFont(font);
        String name = Blocks.NAME[Blocks.PALETTE[game.selectedSlot] & 0xFF];
        g.drawString(name, x0 + total / 2 - g.getFontMetrics().stringWidth(name) / 2, y0 - 6);

        // version
        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(small);
        g.drawString("jarcraft alpha1.0.0", 8, h - 8);

        if (!game.grabbed) {
            String msg = "Click to play  -  Esc releases the mouse";
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(w / 2 - 160, h / 2 - 40, 320, 24);
            g.setColor(Color.WHITE);
            g.setFont(font);
            g.drawString(msg, w / 2 - g.getFontMetrics().stringWidth(msg) / 2, h / 2 - 23);
        }

        if (game.showDebug) {
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRect(6, 6, 240, 84);
            g.setColor(Color.WHITE);
            g.setFont(font);
            int ly = 22;
            g.drawString("jarcraft alpha1.0.0 (" + game.fps + " fps)", 12, ly); ly += 15;
            g.drawString(String.format("XYZ: %.2f / %.2f / %.2f",
                    game.player.x, game.player.y, game.player.z), 12, ly); ly += 15;
            g.drawString(String.format("Chunk: %d %d  Day: %.2f",
                    (int) game.player.x >> 4, (int) game.player.z >> 4,
                    game.dayLight()), 12, ly); ly += 15;
            g.drawString("Facing: " + facing(), 12, ly);
        }
    }

    private String facing() {
        float yaw = game.player.yaw;
        float deg = (float) Math.toDegrees(yaw);
        deg = ((deg % 360) + 360) % 360;
        if (deg < 45 || deg >= 315) return "south (+Z)";
        if (deg < 135) return "west (-X)";
        if (deg < 225) return "north (-Z)";
        return "east (+X)";
    }
}
