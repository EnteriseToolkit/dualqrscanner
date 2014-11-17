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

package ac.robinson.dualqrscanner;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import java.util.Map;

final class DecodeHandler extends Handler {

	private static final String TAG = DecodeHandler.class.getSimpleName();

	private final IDecoderActivity activity;
	private final QRCodeMultiReader multiReader;
	private final Map<DecodeHintType, Object> readerHints;
	private boolean running = true;

	DecodeHandler(IDecoderActivity activity, Map<DecodeHintType, Object> hints) {
		multiReader = new QRCodeMultiReader();
		readerHints = hints;
		this.activity = activity;
	}

	@Override
	public void handleMessage(Message message) {
		if (!running) {
			return;
		}
		switch (message.what) {
			case DecoderActivityHandler.MSG_DECODE:
				decode((byte[]) message.obj, message.arg1, message.arg2);
				break;
			case DecoderActivityHandler.MSG_QUIT:
				running = false;
				Looper.myLooper().quit();
				break;
		}
	}

	/**
	 * Decode the data within the viewfinder rectangle, and time how long it
	 * took. For efficiency, reuse the same reader objects from one decode to
	 * the next.
	 *
	 * @param data   The YUV preview frame.
	 * @param width  The width of the preview frame.
	 * @param height The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height) {
		// see: http://stackoverflow.com/a/16252917/1993220
		// other changes are in CameraManager and CameraConfigurationManager
		if (activity.getCameraManager().getIsPortrait()) {
			byte[] rotatedData = new byte[data.length];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++)
					rotatedData[x * height + height - y - 1] = data[x + y * width];
			}
			int tmp = width;
			//noinspection SuspiciousNameCombination
			width = height;
			height = tmp;
			data = rotatedData;
		}

		long start = System.currentTimeMillis();
		Result[] rawResults = null;
		PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
		if (source != null) {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			try {
				rawResults = multiReader.decodeMultiple(bitmap, readerHints);
			} catch (ReaderException re) {
				// continue
			} finally {
				multiReader.reset();
			}
		}

		Handler handler = activity.getHandler();
		if (rawResults != null && rawResults.length == 2) {
			// Don't log the barcode contents for security.
			long end = System.currentTimeMillis();
			Log.d(TAG, "Found two QR codes in " + (end - start) + " ms");
			if (handler != null) {
				Message message = Message.obtain(handler, DecoderActivityHandler.MSG_DECODE_SUCCEEDED, rawResults);
				message.sendToTarget();
			}
		} else {
			if (handler != null) {
				Message message = Message.obtain(handler, DecoderActivityHandler.MSG_DECODE_FAILED);
				message.sendToTarget();
			}
		}
	}

}
