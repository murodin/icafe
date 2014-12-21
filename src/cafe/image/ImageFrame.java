/**
 * Copyright (c) 2014 by Wen Yu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Any modifications to this file must keep this entire header intact.
 */

package cafe.image;

import java.awt.image.BufferedImage;

/** 
 * Wrapper for an image frame
 * 
 * @author Wen Yu, yuwen_66@yahoo.com
 * @version 1.0 11/21/2014
 */
public class ImageFrame {
	// Frame parameters
	private BufferedImage frame;
	private ImageMeta frameMeta;
	
	public ImageFrame(BufferedImage frame) {
		this(frame, ImageMeta.DEFAULT_IMAGE_META);
	}
	
	public ImageFrame(BufferedImage frame, ImageMeta frameMeta) {
		this.frame = frame;
		this.frameMeta = frameMeta;
	}
	public BufferedImage getFrame() {
		return frame;
	}
	
	public ImageMeta getFrameMeta() {
		return frameMeta;
	}
	
	public void setFrameMeta(ImageMeta frameMeta) {
		this.frameMeta = frameMeta;
	}
}