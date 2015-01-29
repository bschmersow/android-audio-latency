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
import zee.audiobenchmark.datatypes.TestResult;
import zee.audiobenchmark.interfaces.AsyncResponse;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Latency test using the AudioRecord/AudioTrack Java class, an impulse and the system's nanoTimer
 * Executed in an asynchronous thread
 */
public class AudioRecordLatencyTest extends AsyncTask<SystemParameters, Void, TestResult>{

	//values delivered in SystemParameters
	static int sampleRateInHz;
	static int bufferSizeInSamples;
	static int bufferSizeInBytes;

	short[] buffer;

	AudioRecord mpRecord;
	AudioTrack mpTrack;

	//parameters for the test 
	short threshold; //The threshold for the impulse recognition
	int padding; 				//runs between impulses
	int numTests; 				//total runs
	long timeout; 		//seconds until run considered to be timed out

	static String LOG_TAG = "AudioRecordLatencyTest";

	//response to the class that started this thread
	public AsyncResponse delegate=null;
	@Override
	protected void onPostExecute(TestResult result) {
		delegate.processFinish(result);
	}

	//normalizes a timing result to the maximum (worst-case) value
	private long normalizeResult(long val, int pos) {
		//calculate time for a buffersize of size pos
		//this simulates that the impulse was received at position 0
		long elapsed = (pos) / (sampleRateInHz/1000); //in ms
		return (val + elapsed);
	}

	/*
	 * (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 * will be executed in a asynchronous thread
	 */
	@Override
	protected TestResult doInBackground(SystemParameters... params) {
		//retrieve the set sampleRate
		sampleRateInHz = params[0].sampleRate;
		numTests = params[0].numberOfTests;
		timeout = numTests;
		threshold = (short) (Short.MAX_VALUE / params[0].getThresholdDivider());
		bufferSizeInSamples = Integer.parseInt(params[0].selectedBufferSize);
		bufferSizeInBytes = bufferSizeInSamples * 2;
		padding = sampleRateInHz/bufferSizeInSamples; //~1s, which should be enough

		try {
			// Prepare the AudioRecord & AudioTrack
			mpRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
					sampleRateInHz, 
					AudioFormat.CHANNEL_IN_MONO,
					SystemParameters.audioEncoding, 
					bufferSizeInBytes);

			mpTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					sampleRateInHz, 
					AudioFormat.CHANNEL_OUT_MONO,
					SystemParameters.audioEncoding, 
					bufferSizeInBytes,
					AudioTrack.MODE_STREAM);   
			mpTrack.setPlaybackRate(sampleRateInHz);

		} catch (Throwable t) {
			Log.e("Error", "Init Audio Objects; trace: "+t.getLocalizedMessage());
			return new TestResult("Selected buffer size too small, please select a buffer size higher than the minimum to run the AudioTrack class test.");
		}

		//prepare buffers
		buffer = new short[bufferSizeInSamples];
		short[] bufferZeros = new short[bufferSizeInSamples];
		short[] bufferMask = new short[bufferSizeInSamples];

		bufferMask[0] = Short.MAX_VALUE; //The Impulse

		mpTrack.play();
		mpRecord.startRecording();

		long timeStamp = 0l;
		long[] lValues = new long[numTests]; 		//measurements
		long[] lValuesNorm = new long[numTests]; 	//normalized results
		int impRec = 0; 						//received impulses
		boolean done = false;

		Log.i(LOG_TAG, "Starting latency test, threshold: " + threshold);

		/**
		 * Wait for some periods 
		 */
		for(int i=0; i<padding; i++) {
			mpTrack.write(bufferZeros, 0, buffer.length);
			mpRecord.read(buffer, 0, buffer.length);
		}

		/**
		 * Latency measurement 
		 */
		while (!done && !this.isCancelled()) {
			//the function call is defined as "starting time"
			timeStamp = System.nanoTime();
			mpTrack.write(bufferMask, 0, buffer.length);

			//write zeros to output (padding) until impulse mask is received 
			for(int i=0; i<padding; i++) {
				mpRecord.read(buffer, 0, buffer.length);
				//search input buffer for impulse
				for(int k=0; k<buffer.length; k++) {
					short s = buffer[k];
					if(s > threshold) {
						if(impRec < numTests) {
							lValues[impRec] = (System.nanoTime() - timeStamp)/1000000;
							lValuesNorm[impRec] = normalizeResult(lValues[impRec], k); //normalize to array position
							Log.i(LOG_TAG,"rec impulse, time: " + lValues[impRec]+ "ms" + "; normalized: " + lValuesNorm[impRec]);
							impRec++;
							break; //since the impulse may be distorted, following samples are ignored
						} else {
							done = true;
						}
					}
				}
				mpTrack.write(bufferZeros, 0, buffer.length);      		
			}  
			mpRecord.read(buffer, 0, buffer.length);

			//check if timed out
			if((System.nanoTime() - timeStamp) > (timeout*1000000000)) {
				Log.i(LOG_TAG,"timed out.");
				mpRecord.release();
				mpTrack.release();
				return new TestResult("Timed out after " + timeout + "seconds. \n Please check connections and levels.");
			}
		}
		mpRecord.release();
		mpTrack.release();

		return new TestResult(lValues, lValuesNorm, bufferSizeInSamples, 16, sampleRateInHz);
	}
}
