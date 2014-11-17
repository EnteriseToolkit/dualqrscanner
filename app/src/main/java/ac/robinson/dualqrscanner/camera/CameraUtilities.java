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

package ac.robinson.dualqrscanner.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class CameraUtilities {

	@SuppressWarnings("deprecation")
	public static Point getScreenSize(WindowManager windowManager) {
		Point screenSize = new Point();
		Display display = windowManager.getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			getPointScreenSize(display, screenSize);
		} else {
			screenSize.set(display.getWidth(), display.getHeight());
		}
		return screenSize;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private static void getPointScreenSize(Display display, Point screenSize) {
		display.getSize(screenSize);
	}

	// see: http://developer.android.com/reference/android/hardware/Camera.Parameters
	// .html#setRotation(int)
	public static int getPreviewOrientationDegrees(int screenOrientationDegrees, Integer cameraOrientationDegrees,
	                                               boolean usingFrontCamera) {
		int previewOrientationDegrees;
		if (cameraOrientationDegrees != null) {
			if (usingFrontCamera) { // compensate for the mirror of the front camera
				previewOrientationDegrees = (cameraOrientationDegrees + screenOrientationDegrees) % 360;
				previewOrientationDegrees = (360 - previewOrientationDegrees) % 360;
			} else { // back-facing
				previewOrientationDegrees = (cameraOrientationDegrees - screenOrientationDegrees + 360) % 360;
			}
		} else {
			// XXX: can we detect camera orientation some other way?
			// Log.d(TAG, "Unable to detect camera orientation - setting to 0");
			previewOrientationDegrees = 0;
		}
		return previewOrientationDegrees;
	}

	/**
	 * Get the current rotation of the screen, either 0, 90, 180 or 270 degrees
	 */
	public static int getScreenRotationDegrees(WindowManager windowManager) {
		int degrees = 0;
		switch (windowManager.getDefaultDisplay().getRotation()) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
		}
		return degrees;
	}

	public static boolean getIsCameraAvailable(PackageManager packageManager) {
		return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void setScreenOrientationFixed(Activity activity) {
		WindowManager windowManager = activity.getWindowManager();
		boolean naturallyPortrait = getNaturalScreenOrientation(windowManager) == ActivityInfo
				.SCREEN_ORIENTATION_PORTRAIT;
		int reversePortrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
		int reverseLandscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			reversePortrait = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
			reverseLandscape = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
		}
		switch (windowManager.getDefaultDisplay().getRotation()) {
			case Surface.ROTATION_0:
				activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
						ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case Surface.ROTATION_90:
				activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
						reversePortrait);
				break;
			case Surface.ROTATION_180:
				activity.setRequestedOrientation(naturallyPortrait ? reversePortrait : reverseLandscape);
				break;
			case Surface.ROTATION_270:
				activity.setRequestedOrientation(naturallyPortrait ? reverseLandscape : ActivityInfo
						.SCREEN_ORIENTATION_PORTRAIT);
				break;
		}
	}

	/**
	 * Get the "natural" screen orientation - i.e. the orientation in which this device is
	 * designed to be used most
	 * often.
	 *
	 * @param windowManager the windowManager (e.g., (WindowManager) getSystemService(Context.WINDOW_SERVICE))
	 * @return either ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE or ActivityInfo
	 * .SCREEN_ORIENTATION_PORTRAIT
	 */
	@SuppressWarnings("WeakerAccess")
	public static int getNaturalScreenOrientation(WindowManager windowManager) {
		Display display = windowManager.getDefaultDisplay();
		Point screenSize = CameraUtilities.getScreenSize(windowManager);
		int width = 0;
		int height = 0;
		switch (display.getRotation()) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
				width = screenSize.x;
				height = screenSize.y;
				break;
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				//noinspection SuspiciousNameCombination
				width = screenSize.y;
				//noinspection SuspiciousNameCombination
				height = screenSize.x;
				break;
			default:
				break;
		}

		if (width > height) {
			return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		}
		return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	}
}
