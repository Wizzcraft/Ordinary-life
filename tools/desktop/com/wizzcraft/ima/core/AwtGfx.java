package com.wizzcraft.ima.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Desktop renderer used only for testing the core off-device. Not part of the Android app. */
final class AwtGfx implements Gfx {
    final BufferedImage img;
    private final Graphics2D g2;
    private final Font base = new Font("DejaVu Sans", Font.PLAIN, 12);

    AwtGfx(int w, int h, float scale) {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.scale(scale, scale);
    }

    private void col(int argb) {
        g2.setColor(new Color(argb, true));
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        col(argb);
        g2.fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float r, int argb) {
        col(argb);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float r, float sw, int argb) {
        col(argb);
        g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
    }

    @Override
    public void fillPoly(float[] xy, int argb) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        col(argb);
        g2.fill(p);
    }

    @Override
    public void fillCircle(float cx, float cy, float r, int argb) {
        col(argb);
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float sw, int argb) {
        col(argb);
        g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void text(String s, float x, float y, float size, int argb, int align) {
        if (s.isEmpty()) return;
        g2.setFont(base.deriveFont(size));
        float w = (float) g2.getFontMetrics().getStringBounds(s, g2).getWidth();
        float left = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
        col(argb);
        g2.drawString(s, left, y);
    }

    @Override
    public float textWidth(String s, float size) {
        g2.setFont(base.deriveFont(size));
        return (float) g2.getFontMetrics().getStringBounds(s, g2).getWidth();
    }

    @Override
    public void translate(float dx, float dy) {
        g2.translate(dx, dy);
    }

    void save(String path) throws IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
