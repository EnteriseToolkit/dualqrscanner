/*
 * Copyright (C) 2014 Simon Robinson
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

package ac.robinson.dualqrscanner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import java.util.Map;

import ac.robinson.dualqrscanner.camera.BitmapLuminanceSource;

@SuppressWarnings("WeakerAccess")
public class QRImageParser {

	private static final String TAG = QRImageParser.class.getSimpleName();

	private static final String CODE_IDENTIFIER = "(sc)?(\\d+)x(\\d+)";

	private PointF[] mIdPoints = new PointF[3];
	private PointF[] mAlignmentPoints = new PointF[3];
	private PointF mTopLeftInferred;
	private PointF mBottomRightInferred;
	private int mXSquares;
	private int mYSquares;
	private float mGridXScale;
	private float mGridYScale;
	private float mGridXOrigin;
	private float mGridYOrigin;
	private boolean mIsHorizontal;
	private boolean mIsInverted;
	private float mRotation;
	private float mPointSpacing;

	private final ImageParserCallback mImageParserCallback;
	private boolean mResizeImageToView;

	private final QRCodeMultiReader qrReader; // QRCodeReader or MultiFormatReader
	private final Map<DecodeHintType, Object> readerHints;

	public interface ImageParserCallback {
		public void pictureFailed();

		public void pageIdentified(String pageId);

		public void pictureSucceeded(Bitmap result, ImageParameters imageParameters, CodeParameters codeParameters);
	}

	public QRImageParser(ImageParserCallback callback) {
		qrReader = new QRCodeMultiReader();
		readerHints = DecodeThread.getQRReaderHints();

		mImageParserCallback = callback;
	}

	public void parseImage(Bitmap bitmap, Result[] scanResult, Point cameraPreviewSize, int screenRotation,
	                       Point imageViewSize, boolean resizeToView) {
		mResizeImageToView = resizeToView;
		new ImageParserTask(bitmap, scanResult, cameraPreviewSize, screenRotation, imageViewSize).execute();
	}

	private class ImageParserTask extends AsyncTask<Void, String, Bitmap> {
		private Bitmap bitmap;
		private final Result[] scanResult;
		private final Point cameraPreviewSize;
		private final int screenRotation;
		private final Point imageViewSize;

		private ImageParserTask(Bitmap bitmap, Result[] scanResult, Point cameraPreviewSize, int screenRotation,
		                        Point imageViewSize) {
			this.bitmap = bitmap;
			this.scanResult = scanResult;
			this.cameraPreviewSize = cameraPreviewSize;
			this.screenRotation = screenRotation;
			this.imageViewSize = imageViewSize;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		protected Bitmap doInBackground(Void... unused) {
			long start = System.currentTimeMillis(); // debugging/performance tracking
			int imageWidth = bitmap.getWidth();
			int imageHeight = bitmap.getHeight();

			// need to flip x/y values (e.g., width - x; height - y) in 180/270 rotations
			boolean flipXYValues = screenRotation == 180 || screenRotation == 270;

			// get the relative scale of the photo compared to the preview, and scale the found points
			// to their estimated positions on the photo we've taken
			Log.d(TAG, "Preview (" + cameraPreviewSize.x + "," + cameraPreviewSize.y + ") " + "to photo (" +
					imageWidth + "," + imageHeight + ")");
			float[] sourcePoints = new float[]{0, 0, 0, cameraPreviewSize.y, cameraPreviewSize.x, 0,
					cameraPreviewSize.x, cameraPreviewSize.y};
			float[] destinationPoints = {0, 0, 0, imageHeight, imageWidth, 0, imageWidth, imageHeight};
			Matrix imageMatrix = new Matrix();
			imageMatrix.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4);
			for (Result r : scanResult) {
				ResultPoint[] originalPoints = r.getResultPoints();
				ResultPoint[] newPoints = new ResultPoint[originalPoints.length];
				int i = 0;
				for (ResultPoint p : originalPoints) {
					float[] alignmentPoints;
					if (flipXYValues) {
						alignmentPoints = new float[]{cameraPreviewSize.x - p.getX(), cameraPreviewSize.y - p.getY()};
					} else {
						alignmentPoints = new float[]{p.getX(), p.getY()};
					}
					imageMatrix.mapPoints(alignmentPoints);

					ResultPoint newPoint = new ResultPoint(alignmentPoints[0], alignmentPoints[1]);
					newPoints[i] = newPoint;
					i += 1;
				}
				r.addResultPoints(newPoints);
			}

			// now re-scan the photo to find the new code positions (need to deal with both camera
			// movement and preview/photo size mismatch) and the page id & dimensions
			int xDimension = -1;
			int yDimension = -1;
			String pageId = null;
			PointF[] alignmentPoints = new PointF[3];
			PointF[] idPoints = new PointF[3];
			int secondScanArea = Math.max(imageWidth, imageHeight) / 10; // px area around initial code
			RectF imageRect = new RectF(0, 0, imageWidth, imageHeight);
			for (Result r : scanResult) {
				// we should have at least 6 result points (for the smallest QR code size); always even
				ResultPoint[] points = r.getResultPoints();
				if (points.length < 6 || points.length % 2 != 0) {
					return null; // invalid - fail scan
				}

				// get the bounding box, handling codes that have more than 3 points (only need the
				// scaled values, not originals)
				float minX = Float.MAX_VALUE;
				float maxX = Float.MIN_VALUE;
				float minY = Float.MAX_VALUE;
				float maxY = Float.MIN_VALUE;
				for (int i = points.length / 2; i < points.length; i++) {
					float x = points[i].getX();
					float y = points[i].getY();
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}

				// expand estimated box so we're more likely to get the QR code if they moved the phone
				// or the preview/photo aspect ratios differ significantly; crop to image margins
				RectF codeArea = new RectF(minX, minY, maxX, maxY);
				codeArea.inset(-secondScanArea, -secondScanArea);
				RectF clippedCodeArea = new RectF();
				if (!clippedCodeArea.setIntersect(codeArea, imageRect)) {
					return null; // invalid - fail scan
				}
				Rect singleScanArea = new Rect();
				clippedCodeArea.round(singleScanArea);
				Log.d(TAG, "New scan area (clipped): " + singleScanArea.left + "," +
						singleScanArea.top + "," + singleScanArea.right + "," +
						singleScanArea.bottom + ";");

				// find a barcode in this section of the photo
				BitmapLuminanceSource source = new BitmapLuminanceSource(bitmap, singleScanArea.left,
						singleScanArea.top, singleScanArea.right - singleScanArea.left,
						singleScanArea.bottom - singleScanArea.top);
				BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
				Result singleResult = null;
				try {
					// use the multi-qr reader because it is *far* more reliable for close-ups
					singleResult = qrReader.decode(bitmap, readerHints);
				} catch (ReaderException re) {
					return null; // invalid - fail scan
				} finally {
					qrReader.reset();
				}

				// save this result, extracting the code text and corrected points, or fail the scan
				if (singleResult != null) {
					String barcodeText = singleResult.getText();
					Log.d(TAG, "Found code content: " + barcodeText);
					boolean alignmentIdentifier = barcodeText.matches(CODE_IDENTIFIER);
					if (barcodeText.matches(CODE_IDENTIFIER)) {
						String[] dimensions = barcodeText.replaceAll(CODE_IDENTIFIER, "$2," +
								"" + "$3").split(",");
						if (dimensions.length == 2) {
							Log.d(TAG, "Parsing dimension ratio: " + dimensions[0] + "," +
									"" + dimensions[1]);
							try {
								xDimension = Integer.parseInt(dimensions[0]);
								yDimension = Integer.parseInt(dimensions[1]);
							} catch (NumberFormatException e) {
								Log.d(TAG, "Unable to parse dimension ratios (NumberFormatException");
							}
						}
					} else {
						pageId = barcodeText;
					}

					ResultPoint[] finalPoints = singleResult.getResultPoints();
					for (int i = 0; i < finalPoints.length; i++) { // only need the scaled values
						if (alignmentIdentifier) {
							alignmentPoints[i] = new PointF(singleScanArea.left + finalPoints[i].getX(),
									singleScanArea.top + finalPoints[i].getY());
						} else {
							idPoints[i] = new PointF(singleScanArea.left + finalPoints[i].getX(),
									singleScanArea.top + finalPoints[i].getY());
						}
					}
				} else {
					return null; // invalid - fail scan
				}
			}

			// something went wrong - retry
			if (xDimension <= 0 || yDimension <= 0 || pageId == null) {
				return null; // invalid - fail scan
			}

			Log.d(TAG, "Second scan: found codes in " + (System.currentTimeMillis() - start) + "ms");
			publishProgress(pageId);
			mIdPoints = idPoints;
			mAlignmentPoints = alignmentPoints;
			mXSquares = xDimension;
			mYSquares = yDimension;

			// find out the existing rotation, correct for this in code points and infer where top
			// left/bottom right codes would be
			mRotation = getImageRotation(mIdPoints, mAlignmentPoints);
			estimateControlPointsAlignmentAndSpacing();

			// fix the rotation, skew and transformation of the bitmap, and re-scan to get the final
			// code positions - new points as they're modified
			bitmap = correctBitmap(bitmap, mTopLeftInferred, new PointF(mAlignmentPoints[1].x, mAlignmentPoints[1].y),
					new PointF(mIdPoints[1].x, mIdPoints[1].y), mBottomRightInferred, imageViewSize.x,
					imageViewSize.y);

			return bitmap;
		}

		@Override
		protected void onProgressUpdate(String... pageId) {
			if (pageId.length == 1) {
				String id = pageId[0];
				if (id != null) {
					mImageParserCallback.pageIdentified(id);
				} else {
					mImageParserCallback.pictureFailed();
				}
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if (result == null) {
				mImageParserCallback.pictureFailed();
			} else {
				ImageParameters imageParameters = new ImageParameters(mGridXOrigin, mGridYOrigin, mGridXScale,
						mGridYScale, mIsHorizontal, mIsInverted);
				CodeParameters codeParameters = new CodeParameters(mIdPoints, mAlignmentPoints, mPointSpacing);
				mImageParserCallback.pictureSucceeded(result, imageParameters, codeParameters);
			}
		}
	}

	private float getImageRotation(PointF[] idPoints, PointF[] alignmentPoints) {
		// correct for image rotation (assumes that the qr points are always in the same order)
		// - they *are* (see processFinderPatternInfo in Detector.java (ResultPoint[]{bottomLeft,
		// topLeft, topRight};)
		double pi2 = Math.PI / 2;
		double r1 = getRotation(idPoints[0], idPoints[1]) - pi2;
		double r2 = getRotation(idPoints[1], idPoints[2]) - Math.PI;
		double r3 = getRotation(alignmentPoints[0], alignmentPoints[1]) - pi2;
		double r4 = getRotation(alignmentPoints[1], alignmentPoints[2]) - Math.PI;
		float rotation = -getAverageAngle(r1, r2, r3, r4);
		Log.d(TAG, "Image rotation required: " + rotation + " (" + r1 + "," + r2 + "," + r3 + "," +
				"" + r4 + ")");
		return rotation;
	}

	private void estimateControlPointsAlignmentAndSpacing() {
		// infer where the top left and bottom right control points would be
		mTopLeftInferred = getLineIntersection(mIdPoints[0], mIdPoints[1], mAlignmentPoints[2], mAlignmentPoints[1]);
		mBottomRightInferred = getLineIntersection(mIdPoints[1], mIdPoints[2], mAlignmentPoints[1],
				mAlignmentPoints[0]);
		Log.d(TAG, "Calculated top left: " + ((mTopLeftInferred != null) ? (mTopLeftInferred.x +
				"," + mTopLeftInferred.y) : "null"));
		Log.d(TAG, "Calculated bottom right: " + ((mBottomRightInferred != null) ? (mBottomRightInferred.x + "," +
				"" + mBottomRightInferred.y) : "null"));

		// get the orientation of the image
		mIsHorizontal = false;
		mIsInverted = false;
		if (mAlignmentPoints[0].y < mIdPoints[0].y) {
			if (mAlignmentPoints[0].x < mIdPoints[0].x) {
				mIsHorizontal = true;
			}
		} else {
			mIsInverted = true;
			if (mAlignmentPoints[0].x > mIdPoints[0].x) {
				mIsHorizontal = true;
			}
		}

		calculateCodeSpacing();
	}

	private void calculateCodeSpacing() {
		// calculate the spacing between QR code points
		if (mIsHorizontal) {
			mPointSpacing = (Math.abs(mIdPoints[0].x - mIdPoints[1].x) + Math.abs(mIdPoints[1].y - mIdPoints[2].y) +
					Math.abs(mAlignmentPoints[0].x - mAlignmentPoints[1].x) +
					Math.abs(mAlignmentPoints[1].y - mAlignmentPoints[2].y)) / 4f;
		} else {
			mPointSpacing = (Math.abs(mIdPoints[0].y - mIdPoints[1].y) + Math.abs(mIdPoints[1].x - mIdPoints[2].x) +
					Math.abs(mAlignmentPoints[0].y - mAlignmentPoints[1].y) +
					Math.abs(mAlignmentPoints[1].x - mAlignmentPoints[2].x)) / 4f;
		}
		Log.d(TAG, "Average point spacing: " + mPointSpacing);
	}

	private Bitmap correctBitmap(Bitmap bitmap, PointF topLeft, PointF topRight, PointF bottomLeft,
	                             PointF bottomRight, float viewWidth, float viewHeight) {
		int imageWidth = bitmap.getWidth();
		int imageHeight = bitmap.getHeight();

		// scale and centre the image, using the known x/y ratio to correct its size
		// TODO: can we detect the difference between a bent piece of paper and an angled picture?
		int horizontalSquares = mXSquares;
		int verticalSquares = mYSquares;
		if (mIsHorizontal) {
			horizontalSquares = mYSquares;
			verticalSquares = mXSquares;
		}

		// swap x/y scaling for horizontal mode, but prioritise to avoid overflowing bounds
		float padding = 2 * (Math.min(imageWidth, imageHeight) / 20f); // 5% pad to avoid cropping
		float objectWidth, objectHeight;
		if (verticalSquares >= horizontalSquares) {
			objectWidth = imageWidth - padding;
			objectHeight = (objectWidth / (float) horizontalSquares) * verticalSquares;
			if (objectHeight >= imageHeight) {
				objectHeight = imageHeight - padding;
				objectWidth = (objectHeight / (float) verticalSquares) * horizontalSquares;
			}
		} else {
			objectHeight = imageHeight - padding;
			objectWidth = (objectHeight / (float) verticalSquares) * horizontalSquares;
			if (objectWidth >= imageWidth) {
				objectWidth = imageWidth - padding;
				objectHeight = (objectWidth / (float) horizontalSquares) * verticalSquares;
			}
		}
		Log.d(TAG, "Object size: " + objectWidth + "," + objectHeight + " (image: " + imageWidth +
				"," + imageHeight + ")");

		// correct control points for rotation, expand to the entire object, then revert rotation
		PointF centre = new PointF(0, 0);
		double radRot = Math.toRadians(mRotation);
		double cosRot = Math.cos(radRot);
		double sinRot = Math.sin(radRot);
		rotatePoint(topLeft, centre, cosRot, sinRot);
		rotatePoint(topRight, centre, cosRot, sinRot);
		rotatePoint(bottomLeft, centre, cosRot, sinRot);
		rotatePoint(bottomRight, centre, cosRot, sinRot);
		topRight.x += mPointSpacing;
		bottomRight.x += mPointSpacing;
		bottomLeft.y += mPointSpacing;
		bottomRight.y += mPointSpacing;
		radRot = Math.toRadians(-mRotation);
		cosRot = Math.cos(radRot);
		sinRot = Math.sin(radRot);
		rotatePoint(topLeft, centre, cosRot, sinRot);
		rotatePoint(topRight, centre, cosRot, sinRot);
		rotatePoint(bottomLeft, centre, cosRot, sinRot);
		rotatePoint(bottomRight, centre, cosRot, sinRot);

		// create a mapping matrix between our border rect and the desired image size
		// first find a size that is the same ratio as the ImageView, and fits the image
		float desiredWidth;
		float desiredHeight;
		if (objectWidth / viewWidth > objectHeight / viewHeight) {
			desiredWidth = objectWidth + padding;
			desiredHeight = desiredWidth * (viewHeight / viewWidth);
		} else {
			desiredHeight = objectHeight + padding;
			desiredWidth = desiredHeight * (viewWidth / viewHeight);
		}

		// next pick which corners of the image to align where
		float[] destinationPoints;
		PointF averageStart = new PointF(((desiredWidth - objectWidth) / 2f), ((desiredHeight - objectHeight) / 2f));
		PointF averageEnd = new PointF((averageStart.x + objectWidth), (averageStart.y + objectHeight));
		if (mIsHorizontal) {
			if (mIsInverted) {
				// rotated left
				destinationPoints = new float[]{averageEnd.x, averageStart.y, averageStart.x, averageStart.y,
						averageEnd.x, averageEnd.y, averageStart.x, averageEnd.y};
			} else {
				// rotated right
				destinationPoints = new float[]{averageStart.x, averageEnd.y, averageEnd.x, averageEnd.y,
						averageStart.x, averageStart.y, averageEnd.x, averageStart.y};
			}
		} else {
			if (mIsInverted) {
				// portrait, but inverted
				destinationPoints = new float[]{averageEnd.x, averageEnd.y, averageEnd.x, averageStart.y,
						averageStart.x, averageEnd.y, averageStart.x, averageStart.y};
			} else {
				// normal (portrait) mode
				destinationPoints = new float[]{averageStart.x, averageStart.y, averageStart.x, averageEnd.y,
						averageEnd.x, averageStart.y, averageEnd.x, averageEnd.y};
			}
		}

		// transform and rotate the image - see: http://stackoverflow.com/questions/3430368/
		float[] sourcePoints = {topLeft.x, topLeft.y, bottomLeft.x, bottomLeft.y, topRight.x, topRight.y,
				bottomRight.x, bottomRight.y};
		Matrix imageMatrix = new Matrix();
		imageMatrix.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4);
		Log.d(TAG, "Transforming to: " + averageStart.x + "," + averageStart.y + "," +
				averageEnd.y + "," + averageEnd.x);

		int bitmapWidth;
		int bitmapHeight;
		if (mResizeImageToView) {
			float scaleFactor = viewWidth / desiredWidth;
			imageMatrix.postScale(scaleFactor, scaleFactor);
			bitmapWidth = Math.round(desiredWidth * scaleFactor);
			bitmapHeight = Math.round(desiredHeight * scaleFactor);
			Log.d(TAG, "Resizing image to view - scaling by " + scaleFactor + " to " + bitmapWidth + "," +
					"" + bitmapHeight);
		} else {
			bitmapWidth = Math.round(desiredWidth);
			bitmapHeight = Math.round(desiredHeight);
		}

		// correct code points with the final correction matrix
		finaliseCodePositions(imageMatrix);

		// draw the new bitmap
		// TODO: can older devices cope with loading the entire bitmap in memory?
		Bitmap correctedBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, bitmap.getConfig());
		correctedBitmap.eraseColor(Color.TRANSPARENT);
		Canvas canvas = new Canvas(correctedBitmap);
		// canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // alternative for transparency
		canvas.drawBitmap(bitmap, imageMatrix, new Paint());
		bitmap.recycle(); // TODO: note that the original bitmap is now inaccessible
		return correctedBitmap;
	}

	private void finaliseCodePositions(Matrix transformationMatrix) {
		// transform the QR points using the same matrix used for the bitmap
		float[] alignmentPoints = {mAlignmentPoints[0].x, mAlignmentPoints[0].y, mAlignmentPoints[1].x,
				mAlignmentPoints[1].y, mAlignmentPoints[2].x, mAlignmentPoints[2].y};
		transformationMatrix.mapPoints(alignmentPoints);
		for (int i = 0; i < 3; i++) {
			mAlignmentPoints[i].set(alignmentPoints[i * 2], alignmentPoints[(i * 2 + 1)]);
			// Log.d(TAG, "Final alignment point: " + mAlignmentPoints[i].x + ",
			// " + mAlignmentPoints[i].y);
		}
		float[] idPoints = {mIdPoints[0].x, mIdPoints[0].y, mIdPoints[1].x, mIdPoints[1].y, mIdPoints[2].x,
				mIdPoints[2].y};
		transformationMatrix.mapPoints(idPoints);
		for (int i = 0; i < 3; i++) {
			mIdPoints[i].set(idPoints[i * 2], idPoints[(i * 2 + 1)]);
			// Log.d(TAG, "Final id point: " + mIdPoints[i].x + "," + mIdPoints[i].y);
		}

		// update code spacing relative to the new image size
		calculateCodeSpacing();

		// find the new centre positions of the codes
		float alignXCentre = (mAlignmentPoints[0].x + mAlignmentPoints[2].x) / 2;
		float alignYCentre = (mAlignmentPoints[0].y + mAlignmentPoints[2].y) / 2;
		float idXCentre = (mIdPoints[0].x + mIdPoints[2].x) / 2;
		float idYCentre = (mIdPoints[0].y + mIdPoints[2].y) / 2;

		// get the grid size and origin point, correcting for horizontal and inverted photos
		if (mIsHorizontal) {
			mGridXScale = Math.abs(alignYCentre - idYCentre) / (mXSquares - 1);
			mGridYScale = Math.abs(idXCentre - alignXCentre) / (mYSquares - 1);
		} else {
			mGridXScale = Math.abs(alignXCentre - idXCentre) / (mXSquares - 1);
			mGridYScale = Math.abs(idYCentre - alignYCentre) / (mYSquares - 1);
		}

		if (mIsInverted) {
			if (mIsHorizontal) {
				// left landscape
				mGridXOrigin = alignXCentre + (mGridYScale / 2);
				mGridYOrigin = idYCentre - (mGridXScale / 2);
			} else {
				// inverted portrait
				mGridXOrigin = idXCentre + (mGridXScale / 2);
				mGridYOrigin = alignYCentre + (mGridYScale / 2);
			}
		} else {
			if (mIsHorizontal) {
				// right landscape
				mGridXOrigin = alignXCentre - (mGridYScale / 2);
				mGridYOrigin = idYCentre + (mGridXScale / 2);
			} else {
				// normal
				mGridXOrigin = idXCentre - (mGridXScale / 2);
				mGridYOrigin = alignYCentre - (mGridYScale / 2);
			}
		}

		Log.d(TAG, "Code origin finalised to " + mGridXOrigin + "," + mGridYOrigin + " (" +
				mGridXScale + "," + mGridYScale + ") - " + (mIsInverted ? "inverted" : "not " + "inverted") + ", " +
				"" + (mIsHorizontal ? "horizontal" : "vertical"));
	}

	private double getRotation(PointF p1, PointF p2) {
		double delta_x = (p1.x - p2.x);
		double delta_y = (p1.y - p2.y);
		return Math.atan2(delta_y, delta_x);
	}

	private float getAverageAngle(double a1, double a2, double a3, double a4) {
		// see: http://stackoverflow.com/a/5343661
		double x = Math.cos(a1) + Math.cos(a2) + Math.cos(a3) + Math.cos(a4);
		double y = Math.sin(a1) + Math.sin(a2) + Math.sin(a3) + Math.sin(a4);
		return (float) Math.toDegrees(Math.atan2(y, x));
	}

	private PointF getLineIntersection(PointF l1p1, PointF l1p2, PointF l2p1, PointF l2p2) {
		float A1 = l1p2.y - l1p1.y;
		float B1 = l1p1.x - l1p2.x;
		float C1 = A1 * l1p1.x + B1 * l1p1.y;

		float A2 = l2p2.y - l2p1.y;
		float B2 = l2p1.x - l2p2.x;
		float C2 = A2 * l2p1.x + B2 * l2p1.y;

		float det = A1 * B2 - A2 * B1;
		if (Math.abs(det) < 0.00001) {
			return null; // lines are approximately parallel
		} else {
			return new PointF((B2 * C1 - B1 * C2) / det, (A1 * C2 - A2 * C1) / det);
		}
	}

	public static void rotatePoint(PointF point, PointF centre, double cosT, double sinT) {
		float newX = point.x - centre.x;
		float newY = point.y - centre.y;
		point.x = (float) (cosT * newX - sinT * newY) + centre.x;
		point.y = (float) (sinT * newX + cosT * newY) + centre.y;
	}

	@SuppressWarnings("UnusedDeclaration")
	public static PointF getImagePosition(ImageParameters imageParameters, PointF queryLocation) {
		int multiplier = imageParameters.mIsInverted ? -1 : 1;
		float x, y;
		if (imageParameters.mIsHorizontal) {
			x = imageParameters.mGridXOrigin + (multiplier * (queryLocation.y * (imageParameters.mGridXScale / 100f)));
			y = imageParameters.mGridYOrigin - (multiplier * (queryLocation.x * (imageParameters.mGridYScale / 100f)));
		} else {
			x = imageParameters.mGridXOrigin + (multiplier * (queryLocation.x * (imageParameters.mGridXScale / 100f)));
			y = imageParameters.mGridYOrigin + (multiplier * (queryLocation.y * (imageParameters.mGridYScale / 100f)));
		}
		return new PointF(x, y);
	}

	@SuppressWarnings("UnusedDeclaration")
	public static PointF getGridPosition(ImageParameters imageParameters, PointF gridLocation) {
		int multiplier = imageParameters.mIsInverted ? -1 : 1;
		float x, y;
		if (imageParameters.mIsHorizontal) {
			x = (imageParameters.mGridYOrigin - gridLocation.y) / (imageParameters.mGridYScale / 100f) / multiplier;
			y = (gridLocation.x - imageParameters.mGridXOrigin) / (imageParameters.mGridXScale / 100f) / multiplier;
		} else {
			x = (gridLocation.x - imageParameters.mGridXOrigin) / (imageParameters.mGridXScale / 100f) / multiplier;
			y = (gridLocation.y - imageParameters.mGridYOrigin) / (imageParameters.mGridYScale / 100f) / multiplier;
		}
		return new PointF(x, y);
	}
}
