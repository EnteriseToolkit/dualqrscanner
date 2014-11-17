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

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/**
 * Manages beeps for {@link DecoderActivity}.
 */
final class BeepManager {

	private static final String TAG = BeepManager.class.getSimpleName();

	private static final boolean PLAY_BEEP = true;
	private static final float BEEP_VOLUME = 0.10f;

	private final Activity activity;
	private MediaPlayer mediaPlayer;

	BeepManager(Activity activity) {
		this.activity = activity;
		this.mediaPlayer = null;
		initialise();
	}

	void initialise() {
		//noinspection ConstantConditions,PointlessBooleanExpression
		if (PLAY_BEEP && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
			// so we now play on the music stream.
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = buildMediaPlayer(activity);
		}
	}

	void playBeepSound() {
		//noinspection ConstantConditions,PointlessBooleanExpression
		if (PLAY_BEEP && mediaPlayer != null) {
			try {
				mediaPlayer.seekTo(0); // if we played previously, need to rewind
				mediaPlayer.start();
			} catch (Throwable t) {
				mediaPlayer = null;
			}
		}
	}

	private static MediaPlayer buildMediaPlayer(Context activity) {
		MediaPlayer mediaPlayer = new MediaPlayer();
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
		try {
			mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
			file.close();
			mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
			mediaPlayer.prepare();
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			mediaPlayer = null;
		} catch (Throwable t) {
			Log.w(TAG, t);
			mediaPlayer = null;
		}
		return mediaPlayer;
	}

}
