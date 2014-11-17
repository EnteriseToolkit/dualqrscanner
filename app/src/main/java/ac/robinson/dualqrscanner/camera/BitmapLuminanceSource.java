/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.dualqrscanner.camera;

import android.graphics.Bitmap;

import com.google.zxing.LuminanceSource;

/**
 * This LuminanceSource implementation is meant for J2SE clients and our black box unit tests.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class BitmapLuminanceSource extends LuminanceSource {

	private final Bitmap image;
	private final int left;
	private final int top;
	private int[] rgbData;

	public BitmapLuminanceSource(Bitmap image, int left, int top, int width, int height) {
		super(width, height);

		int sourceWidth = image.getWidth();
		int sourceHeight = image.getHeight();
		if (left + width > sourceWidth || top + height > sourceHeight) {
			throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
		}

		this.image = image;
		this.left = left;
		this.top = top;
	}

	// These methods use an integer calculation for luminance derived from:
	// <code>Y = 0.299R + 0.587G + 0.114B</code>
	@Override
	public byte[] getRow(int y, byte[] row) {
		if (y < 0 || y >= getHeight()) {
			throw new IllegalArgumentException("Requested row is outside the image: " + y);
		}
		int width = getWidth();
		if (row == null || row.length < width) {
			row = new byte[width];
		}

		if (rgbData == null || rgbData.length < width) {
			rgbData = new int[width];
		}
		image.getPixels(rgbData, 0, width, left, top + y, width, 1);
		// image.getRGB(left, top + y, width, 1, rgbData, 0, width);
		for (int x = 0; x < width; x++) {
			int pixel = rgbData[x];
			int luminance = (306 * ((pixel >> 16) & 0xFF) + 601 * ((pixel >> 8) & 0xFF) + 117 * (pixel & 0xFF)) >> 10;
			row[x] = (byte) luminance;
		}
		return row;
	}

	@Override
	public byte[] getMatrix() {
		int width = getWidth();
		int height = getHeight();
		int area = width * height;
		byte[] matrix = new byte[area];

		int[] rgb = new int[area];
		image.getPixels(rgb, 0, width, left, top, width, height);
		// image.getRGB(left, top, width, height, rgb, 0, width);
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				int pixel = rgb[offset + x];
				int luminance = (306 * ((pixel >> 16) & 0xFF) + 601 * ((pixel >> 8) & 0xFF) + 117 * (pixel & 0xFF)) >>
						10;
				matrix[offset + x] = (byte) luminance;
			}
		}
		return matrix;
	}
}
