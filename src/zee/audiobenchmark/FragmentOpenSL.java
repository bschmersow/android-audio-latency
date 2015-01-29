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

package zee.audiobenchmark;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * This fragment provides GUI control and access to the native Open SL ES 
 * tests.
 * The Open SL tasks will not run in an asynchronous task like the Java classes, 
 * as the Open SL engine will run the callbacks in its own thread.
 * 
 * @version v0.4 The test output is at this state only visible with the ADB.
 * @author B.Schmersow
 *
 */
public class FragmentOpenSL extends Fragment {

	private static final String LOG_TAG = "FragOpenSL";

	Button btnLoopback = null;
	Button btnSinewave = null;
	Button btnLatency = null;
	TextView twResult = null;

	//native methods, included via JNI
	//see folder jni -> audio-bench-native.c
	public static native boolean nLoopback();
	public static native boolean playSine();
	public static native boolean latencyTest(int numberOfTests);

	public native void createEngine(int bufferSize, int sampleRate, int thresholdDivider);
	public static native void createBufferQueueAudioPlayer();
	public static native void shutdown();
	public static native void resetEngine();
	public static native void createAudioRecorder();

	boolean testActive = false;
	boolean loopActive = false;

	/** Load jni .so on initialization */
	static {
		System.loadLibrary("audioBenchmark");
	}


	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		// initialize native audio system
		createEngine(Integer.parseInt(MainActivity.params.selectedBufferSize), MainActivity.params.sampleRate, MainActivity.params.getThresholdDivider());
		createBufferQueueAudioPlayer();
		createAudioRecorder();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View fragOpenSLView = inflater.inflate(R.layout.fragment_opensl_detail, container, false); 

		twResult = (TextView) fragOpenSLView.findViewById(R.id.twResult);

		//configure Loopback button
		//on press, start native nLoopback() method
		btnLoopback = (Button) fragOpenSLView.findViewById(R.id.btnLoopback);
		btnLoopback.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v) 
			{

				loopActive = !loopActive;
				nLoopback();
				if(loopActive) {
					twResult.setText("Loopback active");
				} else {
					resetEngine();
					twResult.setText("Loopback stopped.");
				}
			}
		}); 

		//configure LatencyTest button
		//on press, start native latencyTest() method
		btnLatency = (Button) fragOpenSLView.findViewById(R.id.btnLatency);
		btnLatency.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v) 
			{
				if(testActive) {
					resetEngine();
					testActive = false;
					btnLatency.setText("Latency Test");
					twResult.setText("Latency test aborted.");
				} else {
					if(latencyTest(MainActivity.params.numberOfTests)) {
						twResult.setText("Testing latency, check log cat output for results.");
						Log.i(LOG_TAG, MainActivity.params.toString());
						testActive = true;
						btnLatency.setText("Active, click to abort");
					} else {
						twResult.setText("Error: could not init output");	
					}
				}
			}
		}); 

		//button for the synthesized sine wave
		btnSinewave = (Button) fragOpenSLView.findViewById(R.id.btnSinewave);
		btnSinewave.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v) 
			{
				if(playSine()) {
				} else {
					twResult.setText("Error: could not register sine wave output");	
				}
			}
		}); 

		return fragOpenSLView;
	}

	@Override 
	public void onDetach() {
		resetEngine();
		shutdown();
		super.onDetach();
	}
}
