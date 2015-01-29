/*
 * Copyright 2014 B.Schmersow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zee.audiobenchmark.tasks;

import zee.audiobenchmark.datatypes.SystemParameters;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Runs an audio loopback (input to output) 
 * using the AudioTrack/AudioRecord classes.
 * @author B. Schmersow
 *
 */
public class AudioRecordLoopback extends AsyncTask<SystemParameters, Void, Void>{

	static int sampleRateInHz;
	static int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

	static int minBufferBytes;
	static int minBufferSmp;
	short[] buffer;

	AudioRecord mpRecord;
	AudioTrack mpTrack;

	static String LOG_TAG = "AudioRecordLoopback";

	@Override
	protected Void doInBackground(SystemParameters... params) {

		//retrieve the systems audio parameters
		sampleRateInHz = params[0].sampleRate;
		minBufferBytes = params[0].minBufferBytes;
		minBufferSmp = params[0].minBufferFrames;

		// Prepare the AudioRecord & AudioTrack
		try {   
			mpRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRateInHz, 
					AudioFormat.CHANNEL_IN_MONO,
					audioEncoding, 
					minBufferBytes);

			mpTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					sampleRateInHz, 
					AudioFormat.CHANNEL_OUT_MONO,
					audioEncoding, 
					minBufferBytes,
					AudioTrack.MODE_STREAM);   
			mpTrack.setPlaybackRate(sampleRateInHz);

		} catch (Throwable t) {
			Log.e("Error", "Init Audio Objects; trace: "+t.getLocalizedMessage());
		}

		buffer = new short[minBufferSmp];

		mpRecord.startRecording();
		Log.i(LOG_TAG,"Audio Recording started");
		mpTrack.play();
		Log.i(LOG_TAG,"Audio Playing started");
		int result;
		while (!this.isCancelled()) {
			result = mpRecord.read(buffer, 0, buffer.length);
			if(result < 0)  Log.w(LOG_TAG, "mpRecord.read() returned " + result);
			result = mpTrack.write(buffer, 0, buffer.length);
			if(result < 0)  Log.w(LOG_TAG, "mpTrack.write() returned " + result);
		}
		return null;
	}
}
