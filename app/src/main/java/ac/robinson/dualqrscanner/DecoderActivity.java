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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import com.google.zxing.Result;

import java.io.IOException;

import ac.robinson.dualqrscanner.camera.CameraManager;
import ac.robinson.dualqrscanner.camera.CameraUtilities;

@SuppressLint("Registered")
public class DecoderActivity extends ActionBarActivity implements IDecoderActivity, SurfaceHolder.Callback {

	private static final String TAG = DecoderActivity.class.getSimpleName();

	private DecoderActivityHandler handler = null;
	private ViewfinderView viewfinderView = null;
	private CameraManager cameraManager = null;
	private boolean hasSurface = false;
	private final String characterSet = null;
	private Result[] savedMultipleBarcodeResult;
	private View imageView;
	private BeepManager beepManager;

	private int viewFinderViewId;
	private int previewViewId;
	private int imageViewId;

	private boolean resizeImageToView;
	private boolean shouldScan;

	/**
	 * This activity handles the backend scanning, recognition and image processing for dual-QR documents.
	 * <p/>
	 * At the moment, for best results it is necessary to:
	 * - in onCreate(), set: getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	 * - in your application's theme, set windowActionBarOverlay to true
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		beepManager = new BeepManager(this);
		resizeImageToView = false;
		shouldScan = true;

		handler = null;
		hasSurface = false;

		// we don't mind which way the screen is set up, as long as it doesn't rotate
		CameraUtilities.setScreenOrientationFixed(DecoderActivity.this);
	}

	/**
	 * Set the IDs of the ViewfinderView and SurfaceView that will be used for the viewfinder and
	 * preview, respectively
	 */
	@SuppressWarnings("unused")
	protected void setViews(int viewFinderView, int previewView, int imageView) {
		this.viewFinderViewId = viewFinderView;
		this.previewViewId = previewView;
		this.imageViewId = imageView;
	}

	/**
	 * Whether to resize the image bitmap to the size of the imageView
	 */
	@SuppressWarnings("unused")
	protected void setResizeImageToView(boolean resize) {
		resizeImageToView = resize;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (shouldScan) {
			startScanning();
		}
	}

	@Override
	protected void onDestroy() {
		beepManager.cleanup();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopScanning();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_CAMERA || super.onKeyDown(keyCode,
				event);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// Ignore
	}

	@Override
	public ViewfinderView getViewfinder() {
		return viewfinderView;
	}

	@Override
	public Handler getHandler() {
		return handler;
	}

	@Override
	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void handlePreviewDecode(Result[] rawResults) {
		savedMultipleBarcodeResult = rawResults; // save for later use
		cameraManager.requestTakePicture(handler, DecoderActivityHandler.MSG_PICTURE);
		onDecodeCompleted();
	}

	/**
	 * Attempts to find QR codes, scaled from the viewfinder recognition, in the picture we took. Looks in the areas
	 * from the original scan, rather than the whole picture, to save memory
	 *
	 * @param data   The JPEG image frame.
	 * @param width  The width of the image.
	 * @param height The height of the image.
	 */
	@Override
	public void handlePictureDecode(byte[] data, int width, int height) {
		Result[] originalResult = savedMultipleBarcodeResult;

		// we scale from the preview size to the photo size
		@SuppressWarnings("deprecation") Camera.Size previewSize = cameraManager.getPreviewResolution();
		if (originalResult == null || originalResult.length != 2 || previewSize == null) {
			pictureFailed();
			return;
		}

		// we rotate the preview data if the camera is portrait; correct for that here
		int previewWidth = previewSize.width;
		int previewHeight = previewSize.height;
		if (cameraManager.getIsPortrait()) {
			//noinspection SuspiciousNameCombination
			previewWidth = previewSize.height;
			//noinspection SuspiciousNameCombination
			previewHeight = previewSize.width;
		}

		// we need to correct points for screen rotation
		int screenRotation = CameraUtilities.getScreenRotationDegrees((WindowManager) getSystemService(Context
				.WINDOW_SERVICE));

		// the image to be processed
		Bitmap decodedBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

		QRImageParser imageParser = new QRImageParser(new QRImageParser.ImageParserCallback() {
			@Override
			public void pictureFailed() {
				DecoderActivity.this.pictureFailed();
			}

			@Override
			public void pageIdentified(String pageId) {
				DecoderActivity.this.pageIdentified(pageId);
			}

			@Override
			public void pictureSucceeded(Bitmap result, ImageParameters imageParameters,
			                             CodeParameters codeParameters) {
				DecoderActivity.this.pictureSucceeded(result, imageParameters, codeParameters);
			}
		});

		imageParser.parseImage(decodedBitmap, savedMultipleBarcodeResult, new Point(previewWidth, previewHeight),
				screenRotation, new Point(imageView.getWidth(), imageView.getHeight()), resizeImageToView);
	}

	private void pictureFailed() {
		//noinspection StatementWithEmptyBody
		if (handler != null) {
			handler.sendEmptyMessage(DecoderActivityHandler.MSG_RESTART_PREVIEW);
		} else {
			// TODO: the activity is probably finishing - is there anything we can do?
		}
		onPictureError();
	}

	private void pageIdentified(String pageId) {
		beepManager.playBeepSound();
		onPageIdFound(pageId);
	}

	private void pictureSucceeded(Bitmap parsedBitmap, ImageParameters imageParameters,
	                              CodeParameters codeParameters) {
		stopScanning();
		shouldScan = false;
		onPictureCompleted(parsedBitmap, imageParameters, codeParameters);
	}

	/**
	 * Called when the initial scan completes - at this stage, the full picture is about to be taken
	 */
	@SuppressWarnings({"WeakerAccess", "EmptyMethod"})
	protected void onDecodeCompleted() {
		// So the implementing activity can show a progress message
	}

	/**
	 * Called when there is an error in taking the picture (this could be from many sources, but is usually due to the
	 * picture not having two recognisable QR codes
	 */
	@SuppressWarnings({"WeakerAccess", "EmptyMethod"})
	protected void onPictureError() {
		// So the implementing activity can show a progress message
	}

	/**
	 * Called when the image has been processed, and its identifiers have been found
	 *
	 * @param pageId the ID of the page that has been scanned
	 */
	@SuppressWarnings({"WeakerAccess", "UnusedParameters", "EmptyMethod"})
	protected void onPageIdFound(String pageId) {
		// So the implementing activity can show a progress message
	}

	/**
	 * Called when picture processing has completed, and the image has been cropped, scaled and aligned
	 *
	 * @param parsedBitmap    the resulting image
	 * @param imageParameters the parameters of the grid on the image (necessary for all applications)
	 * @param codeParameters  the parameters of the QR codes (used for applications that need scale sizes etc)
	 */
	@SuppressWarnings({"WeakerAccess", "UnusedParameters", "EmptyMethod"})
	protected void onPictureCompleted(Bitmap parsedBitmap, ImageParameters imageParameters,
	                                  CodeParameters codeParameters) {
		// So the implementing activity can show a progress message
	}

	@SuppressWarnings("UnusedDeclaration")
	protected void requestScanResume() {
		startScanning();
	}

	private void startScanning() {
		// CameraManager must be initialized here, not in onCreate().
		shouldScan = true;
		if (cameraManager == null) cameraManager = new CameraManager(getApplication());

		if (viewfinderView == null) {
			viewfinderView = (ViewfinderView) findViewById(viewFinderViewId);
			viewfinderView.setCameraManager(cameraManager);
		}
		viewfinderView.setVisibility(View.VISIBLE);

		if (imageView == null) {
			imageView = findViewById(imageViewId);
		}

		SurfaceView surfaceView = (SurfaceView) findViewById(previewViewId);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the camera.
			surfaceHolder.addCallback(this);
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				//noinspection deprecation
				surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			}
		}
		surfaceView.setVisibility(View.VISIBLE);
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (CameraUtilities.getIsCameraAvailable(getPackageManager())) {
			try {
				cameraManager.openDriver(surfaceHolder);
				// Creating the handler starts the preview, which can also throw a RuntimeException.
				if (handler == null) handler = new DecoderActivityHandler(this, characterSet, cameraManager);
			} catch (IOException ioe) {
				Log.w(TAG, ioe);
			} catch (RuntimeException e) {
				// Barcode Scanner has seen crashes in the wild of this variety:
				// java.lang.RuntimeException: Fail to connect to camera service
				Log.w(TAG, "Unexpected error initializing camera", e);
			}
		} else {
			Log.w(TAG, "No camera available; unable to initialise");
		}
	}

	private void stopScanning() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}

		cameraManager.closeDriver();
		if (viewfinderView != null) {
			viewfinderView.setVisibility(View.GONE);
		}

		SurfaceView surfaceView = (SurfaceView) findViewById(previewViewId);
		if (surfaceView != null) {
			if (!hasSurface) {
				SurfaceHolder surfaceHolder = surfaceView.getHolder();
				surfaceHolder.removeCallback(this);
			}
			surfaceView.setVisibility(View.GONE);
		}
	}
}
