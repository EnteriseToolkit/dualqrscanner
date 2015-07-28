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

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.Result;

import ac.robinson.dualqrscanner.camera.CameraManager;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class DecoderActivityHandler extends Handler {

	public static final int MSG_DECODE = 1;
	public static final int MSG_QUIT = 2;
	private static final int MSG_AUTO_FOCUS = 3;
	public static final int MSG_DECODE_FAILED = 4;
	public static final int MSG_DECODE_SUCCEEDED = 5;
	public static final int MSG_RESTART_PREVIEW = 6;
	private static final int MSG_RETURN_SCAN_RESULT = 7;
	public static final int MSG_PICTURE = 8;
	private static final String TAG = DecoderActivityHandler.class.getSimpleName();
	private final IDecoderActivity activity;
	private final DecodeThread decodeThread;
	private final CameraManager cameraManager;
	private State state;

	DecoderActivityHandler(IDecoderActivity activity, String characterSet, CameraManager cameraManager) {
		this.activity = activity;
		decodeThread = new DecodeThread(activity, characterSet, new ViewfinderResultPointCallback(activity
				.getViewfinder()));
		decodeThread.start();
		state = State.SUCCESS;

		// Start ourselves capturing previews and decoding.
		this.cameraManager = cameraManager;
		cameraManager.startPreview();
		restartPreviewAndDecode();
	}

	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
			case MSG_AUTO_FOCUS:
				// Log.d(TAG, "Got auto-focus message");
				// When one auto focus pass finishes, start another. This is the  closest thing to
				// continuous AF. It does seem to hunt a bit, but I'm not sure what else to do.
				if (state == State.PREVIEW) cameraManager.requestAutoFocus(this, MSG_AUTO_FOCUS);
				break;
			case MSG_RESTART_PREVIEW:
				Log.d(TAG, "Got restart preview message");
				restartPreviewAndDecode();
				break;
			case MSG_DECODE_SUCCEEDED:
				Log.d(TAG, "Got decode succeeded message");
				state = State.SUCCESS;
				activity.handlePreviewDecode((Result[]) message.obj);
				break;
			case MSG_DECODE_FAILED:
				// We're decoding as fast as possible, so when one decode fails, start another.
				state = State.PREVIEW;
				cameraManager.requestPreviewFrame(decodeThread.getHandler(), MSG_DECODE);
				break;
			case MSG_PICTURE:
				Log.d(TAG, "Got decode picture message");
				state = State.SUCCESS; // TODO: do we need a "picture" state?
				activity.handlePictureDecode((byte[]) message.obj, message.arg1, message.arg2);
				break;
			case MSG_RETURN_SCAN_RESULT:
				Log.d(TAG, "Got return scan result message");
				if (activity instanceof Activity) {
					((Activity) activity).setResult(Activity.RESULT_OK, (Intent) message.obj);
					((Activity) activity).finish();
				} else {
					Log.e(TAG, "Scan result message, activity is not Activity. Doing nothing.");
				}
				break;
		}
	}

	public void quitSynchronously() {
		Log.d(TAG, "QUITTING");
		state = State.DONE;
		cameraManager.stopPreview();
		Message quit = Message.obtain(decodeThread.getHandler(), MSG_QUIT);
		quit.sendToTarget();
		try {
			// Wait at most half a second; should be enough time, and onPause() will timeout
			// quickly
			decodeThread.join(500L);
		} catch (InterruptedException e) {
			// continue
		}

		// Be absolutely sure we don't send any queued up messages
		removeMessages(MSG_DECODE_SUCCEEDED);
		removeMessages(MSG_DECODE_FAILED);
		removeMessages(MSG_PICTURE);
	}

	private void restartPreviewAndDecode() {
		if (state == State.SUCCESS) {
			state = State.PREVIEW;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), MSG_DECODE);
			cameraManager.requestAutoFocus(this, MSG_AUTO_FOCUS);
			activity.getViewfinder().drawViewfinder();
		}
	}

	private enum State {
		PREVIEW, SUCCESS, DONE
	}
}
