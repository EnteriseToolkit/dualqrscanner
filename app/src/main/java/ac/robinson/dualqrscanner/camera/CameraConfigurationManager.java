/*
 * Copyright (C) 2010 ZXing authors
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

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.util.Log;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A class which deals with reading, parsing, and setting the camera parameters
 * which are used to configure the camera hardware.
 */
@SuppressWarnings("deprecation") // Camera is now deprecated; leave this until a move to Camera2
final class CameraConfigurationManager {

	private static final String TAG = CameraConfigurationManager.class.getSimpleName();
	private static final int MIN_PIXELS = 320 * 240;
	private static final double MAX_ASPECT_DISTORTION = 0.15;

	public static final boolean KEY_REVERSE_IMAGE = false;
	private static final boolean KEY_FRONT_LIGHT = false;

	private final Context context;
	private Point screenResolution;
	private Point cameraResolution;
	private Point pictureResolution;

	public CameraConfigurationManager(Context context) {
		this.context = context;
	}

	private static void initializeTorch(Camera.Parameters parameters) {
		doSetTorch(parameters, KEY_FRONT_LIGHT);
	}

	private static void doSetTorch(Camera.Parameters parameters, boolean newSetting) {
		String flashMode;
		if (newSetting) {
			flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_TORCH,
					Camera.Parameters.FLASH_MODE_ON);
		} else {
			flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_OFF);
		}
		if (flashMode != null) {
			parameters.setFlashMode(flashMode);
		}
	}

	private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
		Log.i(TAG, "Supported values: " + supportedValues);
		String result = null;
		if (supportedValues != null) {
			for (String desiredValue : desiredValues) {
				if (supportedValues.contains(desiredValue)) {
					result = desiredValue;
					break;
				}
			}
		}
		Log.i(TAG, "Settable value: " + result);
		return result;
	}

	private static List<Camera.Size> sortSizes(List<Camera.Size> rawSupportedSizes) {
		// Sort by size, descending
		List<Camera.Size> supportedPreviewSizes = new ArrayList<>(rawSupportedSizes);
		Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
			@Override
			public int compare(Camera.Size a, Camera.Size b) {
				int aPixels = a.height * a.width;
				int bPixels = b.height * b.width;
				if (bPixels < aPixels) {
					return -1;
				}
				if (bPixels > aPixels) {
					return 1;
				}
				return 0;
			}
		});
		return supportedPreviewSizes;
	}

	private static Point findBestSizeValue(List<Camera.Size> supportedSizes, Camera.Size defaultSize,
	                                       Point screenResolution) {
		Point bestSize = null;
		if (supportedSizes != null) {
			List<Camera.Size> sortedSizes = sortSizes(supportedSizes);
			Iterator<Camera.Size> it = sortedSizes.iterator();
			double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;
			boolean isPortrait = screenResolution.x < screenResolution.y;
			while (it.hasNext()) {
				Camera.Size supportedPreviewSize = it.next();
				int pixels = supportedPreviewSize.height * supportedPreviewSize.width;
				if (pixels < MIN_PIXELS) {
					it.remove();
					continue;
				}
				int supportedWidth = isPortrait ? supportedPreviewSize.height : supportedPreviewSize.width;
				int supportedHeight = isPortrait ? supportedPreviewSize.width : supportedPreviewSize.height;
				double aspectRatio = (double) supportedWidth / (double) supportedHeight;
				double distortion = Math.abs(aspectRatio - screenAspectRatio);
				if (distortion > MAX_ASPECT_DISTORTION) {
					it.remove();
				}
			}
			if (!sortedSizes.isEmpty()) {
				Camera.Size largestPreview = sortedSizes.get(0);
				bestSize = new Point(largestPreview.width, largestPreview.height);
				Log.i(TAG, "Using largest suitable preview size: " + bestSize);
			}
		}
		if (bestSize == null) {
			bestSize = new Point(defaultSize.width, defaultSize.height);
			Log.i(TAG, "Using default preview size: " + bestSize);
		}
		return bestSize;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		screenResolution = CameraUtilities.getScreenSize(manager);
		Log.i(TAG, "Screen resolution: " + screenResolution);

		// see: http://stackoverflow.com/a/16252917/1993220
		// other changes are in CameraManager and DecodeHandler
		cameraResolution = findBestSizeValue(parameters.getSupportedPreviewSizes(), parameters.getPreviewSize(),
				screenResolution);
		Log.i(TAG, "Camera resolution: " + cameraResolution);
		pictureResolution = findBestSizeValue(parameters.getSupportedPictureSizes(), parameters.getPictureSize(),
				screenResolution);
		Log.i(TAG, "Picture resolution: " + pictureResolution);
	}

	void setDesiredCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();

		// don't inspect, because the documentation lies - we have encountered null parameters
		//noinspection ConstantConditions
		if (parameters == null) {
			Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
			return;
		}

		initializeTorch(parameters);
		String focusMode = findSettableValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_AUTO,
				Camera.Parameters.FOCUS_MODE_MACRO);
		if (focusMode != null) {
			parameters.setFocusMode(focusMode);
		}

		parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
		parameters.setPictureSize(pictureResolution.x, pictureResolution.y);

		int cameraOrientation = 90; // default before v9
		boolean cameraFrontFacing = false;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			CameraInfo cameraInfo = new CameraInfo();
			Camera.getCameraInfo(0, cameraInfo);
			cameraOrientation = cameraInfo.orientation;
			cameraFrontFacing = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
		}
		int screenRotation = CameraUtilities.getScreenRotationDegrees((WindowManager) context.getSystemService(Context
				.WINDOW_SERVICE));
		int displayOrientation = CameraUtilities.getPreviewOrientationDegrees(screenRotation, cameraOrientation,
				cameraFrontFacing);
		camera.setDisplayOrientation(displayOrientation);


		// see: http://developer.android.com/reference/android/hardware/Camera.Parameters
		// .html#setRotation(int)
		int orientation = screenRotation;
		orientation = (orientation + 45) / 90 * 90;
		int rotation;
		if (cameraFrontFacing) {
			rotation = (cameraOrientation - orientation + 360) % 360;
		} else {  // back-facing camera
			rotation = (cameraOrientation + orientation) % 360;
			boolean isLandscape = screenResolution.y < screenResolution.x;
			if (isLandscape) {
				// for some reason this is required (at least on Nexus 5)
				rotation = (rotation + 180) % 360;
			}
		}
		parameters.setRotation(rotation);

		camera.setParameters(parameters);
	}

	public Point getCameraResolution() {
		return cameraResolution;
	}

	public Point getScreenResolution() {
		return screenResolution;
	}

	public Point getPictureResolution() {
		return pictureResolution;
	}

	@SuppressWarnings("unused")
	void setTorch(Camera camera, boolean newSetting) {
		Camera.Parameters parameters = camera.getParameters();
		doSetTorch(parameters, newSetting);
		camera.setParameters(parameters);
	}
}
