/*
 * Copyright (C) 2008 ZXing authors
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

import ac.robinson.dualqrscanner.camera.CameraManager;
import ac.robinson.dualqrscanner.camera.CameraUtilities;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

	private static final long ANIMATION_DELAY = 80L;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final int MAX_RESULT_POINTS = 20;
	private static final int POINT_SIZE = 10;
	private final Paint paint;
	private int resultPointColor;
	private CameraManager cameraManager;
	private List<ResultPoint> possibleResultPoints;
	private List<ResultPoint> lastPossibleResultPoints;
	private final boolean flipXYValues;

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Initialize these once for performance rather than calling them every time in onDraw().
		resultPointColor = Color.WHITE;
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(resultPointColor);
		possibleResultPoints = new ArrayList<>(5);
		lastPossibleResultPoints = null;

		// as in decoder, need to flip x/y values (e.g., width - x; height - y) in 180/270 rotations
		int screenRotation = CameraUtilities.getScreenRotationDegrees((WindowManager) context.getSystemService(Context
				.WINDOW_SERVICE));
		flipXYValues = screenRotation == 180 || screenRotation == 270;
	}

	@SuppressWarnings("unused")
	public void setResultPointColour(int colour) {
		resultPointColor = colour;
		paint.setColor(resultPointColor);
	}

	public void setCameraManager(CameraManager cameraManager) {
		this.cameraManager = cameraManager;
	}

	@SuppressLint("DrawAllocation")
	@Override
	public void onDraw(Canvas canvas) {
		if (isInEditMode()) {
			return; // so the visual editor can render this view
		}

		Rect frame = cameraManager.getFramingRect();
		if (frame == null) {
			return;
		}

		// Draw the result points
		Rect previewFrame = cameraManager.getFramingRectInPreview();
		float scaleX = frame.width() / (float) previewFrame.width();
		float scaleY = frame.height() / (float) previewFrame.height();

		List<ResultPoint> currentPossible = possibleResultPoints;
		List<ResultPoint> currentLast = lastPossibleResultPoints;
		int frameLeft = frame.left;
		int frameTop = frame.top;
		int frameWidth = frame.width();
		int frameHeight = frame.height();
		if (currentPossible.isEmpty()) {
			lastPossibleResultPoints = null;
		} else {
			possibleResultPoints = new ArrayList<>(5);
			lastPossibleResultPoints = currentPossible;
			paint.setAlpha(CURRENT_POINT_OPACITY);
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (currentPossible) {
				if (flipXYValues) {
					for (ResultPoint point : currentPossible) {
						canvas.drawCircle(frameWidth - (frameLeft + (int) (point.getX() * scaleX)),
								frameHeight - (frameTop + (int) (point.getY() * scaleY)), POINT_SIZE, paint);
					}
				} else {
					for (ResultPoint point : currentPossible) {
						canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop + (int) (point.getY() *
								scaleY), POINT_SIZE, paint);
					}
				}
			}
		}
		if (currentLast != null) {
			paint.setAlpha(CURRENT_POINT_OPACITY / 2);
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (currentLast) {
				if (flipXYValues) {
					for (ResultPoint point : currentLast) {
						canvas.drawCircle(frameWidth - (frameLeft + (int) (point.getX() * scaleX)),
								frameHeight - (frameTop + (int) (point.getY() * scaleY)), POINT_SIZE / 2, paint);
					}
				} else {
					for (ResultPoint point : currentLast) {
						canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop + (int) (point.getY() *
								scaleY), POINT_SIZE / 2, paint);
					}
				}
			}
		}

		// Request another update at the animation interval, but only repaint the framed area,
		// not the entire viewfinder mask.
		postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE, frame.top - POINT_SIZE,
				frame.right + POINT_SIZE, frame.bottom + POINT_SIZE);
	}

	public void drawViewfinder() {
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		List<ResultPoint> points = possibleResultPoints;
		//noinspection SynchronizationOnLocalVariableOrMethodParameter
		synchronized (point) {
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS) {
				// trim it
				points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}
}
