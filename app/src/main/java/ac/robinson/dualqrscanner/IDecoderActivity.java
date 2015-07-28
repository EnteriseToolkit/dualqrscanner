/*
 * Copyright (C) 2011 Justin Wetherell
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

import com.google.zxing.Result;

import ac.robinson.dualqrscanner.camera.CameraManager;

interface IDecoderActivity {

	ViewfinderView getViewfinder();

	Handler getHandler();

	CameraManager getCameraManager();

	void handlePreviewDecode(Result[] rawResults);

	@SuppressWarnings("UnusedParameters")
	void handlePictureDecode(byte[] data, int width, int height);
}
