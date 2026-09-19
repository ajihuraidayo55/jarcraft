import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Tiny helper: capture the X display to a PNG (for smoke tests). */
public class Shot {
    public static void main(String[] args) throws Exception {
        Robot r = new Robot();
        BufferedImage img = r.createScreenCapture(new java.awt.Rectangle(0, 0, 1024, 768));
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("saved " + args[0]);
    }
}
