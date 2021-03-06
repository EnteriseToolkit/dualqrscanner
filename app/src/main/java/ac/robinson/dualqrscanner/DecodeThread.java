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

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class DecodeThread extends Thread {

	private final IDecoderActivity activity;
	private final Map<DecodeHintType, Object> hints;
	private final CountDownLatch handlerInitLatch;

	private Handler handler;

	DecodeThread(IDecoderActivity activity, String characterSet, ResultPointCallback resultPointCallback) {
		this.activity = activity;
		handlerInitLatch = new CountDownLatch(1);
		hints = getQRReaderHints();
		if (characterSet != null) {
			hints.put(DecodeHintType.CHARACTER_SET, characterSet);
		}
		hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
	}

	public static Map<DecodeHintType, Object> getQRReaderHints() {
		Map<DecodeHintType, Object> hints;
		hints = new EnumMap<>(DecodeHintType.class);
		Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
		decodeFormats.addAll(EnumSet.of(BarcodeFormat.QR_CODE));
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
		hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
		return hints;
	}

	Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeHandler(activity, hints);
		handlerInitLatch.countDown();
		Looper.loop();
	}
}
