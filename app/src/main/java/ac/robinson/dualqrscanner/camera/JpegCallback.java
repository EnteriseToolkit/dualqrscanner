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

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

@SuppressWarnings("deprecation")
final class JpegCallback implements Camera.PictureCallback {

	private static final String TAG = JpegCallback.class.getSimpleName();

	private final CameraConfigurationManager configManager;
	private Handler jpegHandler;
	private int jpegMessage;

	JpegCallback(CameraConfigurationManager configManager) {
		this.configManager = configManager;
	}

	void setHandler(Handler previewHandler, int previewMessage) {
		this.jpegHandler = previewHandler;
		this.jpegMessage = previewMessage;
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		Point pictureResolution = configManager.getPictureResolution();
		Handler thePreviewHandler = jpegHandler;
		if (pictureResolution != null && thePreviewHandler != null) {
			Message message = thePreviewHandler.obtainMessage(jpegMessage, pictureResolution.x, pictureResolution.y,
					data);
			message.sendToTarget();
			jpegHandler = null;
		} else {
			Log.d(TAG, "Got jpeg callback, but no handler or resolution available");
		}
		camera.startPreview();
	}
}
