package com.fourcut.photo.composite;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Draws a source image into a destination rect, scaled and center-cropped to fully cover it. */
final class ImageCompositor {

	private ImageCompositor() {
	}

	static void drawCover(Graphics2D canvasGraphics, BufferedImage src, Rectangle dest) {
		double scale = Math.max((double) dest.width / src.getWidth(), (double) dest.height / src.getHeight());
		int scaledWidth = (int) Math.round(src.getWidth() * scale);
		int scaledHeight = (int) Math.round(src.getHeight() * scale);
		int offsetX = (dest.width - scaledWidth) / 2;
		int offsetY = (dest.height - scaledHeight) / 2;

		Graphics2D slotGraphics = (Graphics2D) canvasGraphics.create(dest.x, dest.y, dest.width, dest.height);
		try {
			slotGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			slotGraphics.drawImage(src, offsetX, offsetY, scaledWidth, scaledHeight, null);
		} finally {
			slotGraphics.dispose();
		}
	}
}
