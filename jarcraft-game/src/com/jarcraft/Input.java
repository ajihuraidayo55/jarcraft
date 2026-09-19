package com.jarcraft;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/** Keyboard + mouse state collector. */
public final class Input implements java.awt.event.KeyListener,
        MouseListener, MouseMotionListener {

    private final boolean[] down = new boolean[600];
    public boolean[] mouseDown = new boolean[4];

    public int dx, dy;          // accumulated mouse movement while grabbed
    public boolean clicked;     // any click while not grabbed (to grab)

    private int lastX = -1, lastY = -1; // last raw screen pos, -1 = unknown

    public boolean down(int vk) {
        return vk >= 0 && vk < down.length && down[vk];
    }

    public boolean mouseDown(int btn) {
        return btn >= 1 && btn < mouseDown.length && mouseDown[btn];
    }

    public void consumeMouse() {
        dx = 0;
        dy = 0;
    }

    public void consumeClick() { clicked = false; }

    @Override public void keyTyped(KeyEvent e) {}

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() < down.length) down[e.getKeyCode()] = true;
    }

    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() < down.length) down[e.getKeyCode()] = false;
    }

    @Override public void mouseClicked(MouseEvent e) {}

    @Override public void mousePressed(MouseEvent e) {
        int b = e.getButton();
        if (b >= 1 && b < mouseDown.length) mouseDown[b] = true;
        clicked = true;
    }

    @Override public void mouseReleased(MouseEvent e) {
        int b = e.getButton();
        if (b >= 1 && b < mouseDown.length) mouseDown[b] = false;
    }

    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override public void mouseDragged(MouseEvent e) { track(e); }

    @Override public void mouseMoved(MouseEvent e) { track(e); }

    private void track(MouseEvent e) {
        int nx = e.getXOnScreen(), ny = e.getYOnScreen();
        if (lastX != -1 && lastY != -1) {
            int ddx = nx - lastX, ddy = ny - lastY;
            // ignore recentring jumps from the Robot warp
            if (Math.abs(ddx) < 300 && Math.abs(ddy) < 300) {
                dx += ddx;
                dy += ddy;
            }
        }
        lastX = nx;
        lastY = ny;
    }

    public void resetMouseOrigin() {
        lastX = -1;
        lastY = -1;
    }
}
