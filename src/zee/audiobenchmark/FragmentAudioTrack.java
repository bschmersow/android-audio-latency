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

import zee.audiobenchmark.datatypes.TestResult;
import zee.audiobenchmark.interfaces.AsyncResponse;
import zee.audiobenchmark.tasks.AudioRecordLatencyTest;
import zee.audiobenchmark.tasks.AudioRecordLoopback;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Provides the GUI for the tests with the AudioTrack and AudioRecord class
 * @author zee
 */
public class FragmentAudioTrack extends Fragment implements AsyncResponse {

	//the parent activity
	MainActivity act;
	private static final String LOG_TAG = "FragAudioTrack";

	//The loopback and latency test are started in AsyncThread implementations 
	AudioRecordLoopback loopbackThread = null;
	AudioRecordLatencyTest latencyThread = null;
	boolean loopbackActive = false;
	boolean testActive = false;

	private Button btnLoopback = null;
	private Button btnLatency = null;
	private TextView twResults = null;

	TestResult results;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);	
		act = (MainActivity)activity;
	}


	private boolean bufferSizeValid() {
		if(MainActivity.params.minBufferFrames > Integer.parseInt(MainActivity.params.selectedBufferSize)) {
			twResults.setText("Selected buffer size too small for AudioTrack");
			return false;
		}
		return true;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View fragStdAPIView = inflater.inflate(R.layout.fragment_standard_detail, container, false); 

		//configure "loopback" button
		//on press, an async AudioRecordLoopback thread is started
		btnLoopback = (Button) fragStdAPIView.findViewById(R.id.btnLoopback);
		btnLoopback.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v) 
			{
				if(!loopbackActive) {
					if(!bufferSizeValid()) return;
					cancelTestIfActive();
					loopbackActive = true;
					Toast.makeText(act.getApplicationContext(), "Loopback enabled", Toast.LENGTH_LONG).show();
					Log.d(LOG_TAG,"Starting loopback async thread");
					btnLoopback.setText("Loopback active...");
					loopbackThread = new AudioRecordLoopback();
					loopbackThread.execute(MainActivity.params);
				} else {
					cancelLoopbackIfActive();
				}
			}
		}); 

		//configure "latency" button
		//on press, an async AudioRecordLatencyTests thread is started
		btnLatency = (Button) fragStdAPIView.findViewById(R.id.btnLatency);
		final FragmentAudioTrack parent = this;
		btnLatency.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v) 
			{
				if(!testActive) {
					if(!bufferSizeValid()) return;
					cancelLoopbackIfActive();
					testActive = true;
					Log.i(LOG_TAG, MainActivity.params.toString());
					twResults.setText("please wait...");
					latencyThread = new AudioRecordLatencyTest();
					latencyThread.delegate = parent;
					latencyThread.execute(MainActivity.params);
				} else {
					cancelTestIfActive();
				}
			}
		}); 

		//in case that the results doesnt fit on the screen, make it scrollable
		twResults = (TextView) fragStdAPIView.findViewById(R.id.twResult);
		twResults.setMovementMethod(new ScrollingMovementMethod());


		return fragStdAPIView;
	}

	@Override 
	public void onDetach() {
		cancelLoopbackIfActive();
		cancelTestIfActive();
		super.onDetach();
	}

	private void cancelLoopbackIfActive(){
		if(loopbackActive && loopbackThread != null) {
			Toast.makeText(act.getApplicationContext(), "Loopback stopped", Toast.LENGTH_LONG).show();
			loopbackThread.cancel(true);
			btnLoopback.setText("Loopback");
			loopbackActive = false;
			loopbackThread = null;
		}
	}

	private void cancelTestIfActive(){
		if(testActive && latencyThread != null) {
			Toast.makeText(act.getApplicationContext(), "Latency test stopped", Toast.LENGTH_LONG).show();
			latencyThread.cancel(true);
			twResults.setText("canceled.");
			testActive = false;
			latencyThread = null;
		}
	}

	@Override
	public void processFinish(TestResult result) {
		twResults.setText(result.getFormatedTestOutput());
		Log.i(LOG_TAG,"Latency test summary: " + result.getFormatedTestOutput());
		btnLoopback.setText("Loopback");
		testActive = false;
	}
}
