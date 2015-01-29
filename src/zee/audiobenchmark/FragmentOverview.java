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

import java.io.IOException;

import android.app.Activity;
import android.app.Fragment;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;


/**
 * Aquires and displays information concerning the operating system and audio. *
 */
public class FragmentOverview extends Fragment{	

	MainActivity act;
	MediaPlayer mp = null;
	boolean mpActive = false;
	static String LOG_TAG = "FragmentOverview";

	Spinner spSamplerate;
	Spinner spThreshold;
	Spinner spBuffersize;
	Spinner spNumberOfTests;

	TextView twMinBuffersize;

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public FragmentOverview() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		act = (MainActivity)activity;
	}

	/**
	 * Fill fields with information provided by SystemParameters class
	 * Spinners provide action listeners to change the settings
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View fragOvView = inflater.inflate(R.layout.fragment_overview_detail, container, false); 

		Button btnPlayTestSound = (Button) fragOvView.findViewById(R.id.btnOvTestSound);
		btnPlayTestSound.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				Log.d(LOG_TAG, "Playing test sound");

				if(!mpActive) {
					try {
						mp = MediaPlayer.create(act, R.raw.testsound);
						mp.prepare();
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					mp.seekTo(0);
					mp.start();
					mpActive = true;
				} else {
					try {
						mp.stop();
						mp.release();
					} catch(Exception e) {}
					mpActive = false;
				}
			}
		});

		//The spinner to select the samplerate. Default setting is set in system parameters
		spSamplerate = (Spinner) fragOvView.findViewById(R.id.spSamplerate);
		ArrayAdapter<String> sRates = new ArrayAdapter<String>(act, android.R.layout.simple_spinner_item, MainActivity.params.availableSamplerates);
		sRates.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spSamplerate.setAdapter(sRates);
		spSamplerate.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				String selected = (String) parent.getItemAtPosition(pos);
				MainActivity.params.sampleRate = Integer.parseInt(selected);
				((TextView)act.findViewById(R.id.twSampleRate)).setText("Samplerate: (native) " + MainActivity.params.defaultSampleRate + "Hz, (selected) " + selected + "Hz");
				//minimum buffer size may have changed, reset
				MainActivity.params.calcAndSetMinBufferSize();
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		//Search and set active item to selected samplerate 
		int posSamplerate = sRates.getPosition(Integer.toString(MainActivity.params.sampleRate));
		spSamplerate.setSelection(posSamplerate);


		//The spinner to select the buffersize
		twMinBuffersize = (TextView) fragOvView.findViewById(R.id.twMinBuffersize);
		twMinBuffersize.setText("AudioTrack: min buffer size (bytes):" + MainActivity.params.minBufferBytes + "\n Selected buffer size(frames): " + MainActivity.params.selectedBufferSize);

		spBuffersize = (Spinner) fragOvView.findViewById(R.id.spBuffersize);
		ArrayAdapter<String> buffersizes = new ArrayAdapter<String>(act, android.R.layout.simple_spinner_item, MainActivity.params.bufferSizes);
		buffersizes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spBuffersize.setAdapter(buffersizes);
		spBuffersize.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				String selected = (String) parent.getItemAtPosition(pos);
				MainActivity.params.selectedBufferSize = selected;
				twMinBuffersize.setText("AudioTrack: min buffer size (bytes)/(samples):" + MainActivity.params.minBufferBytes+"/"+MainActivity.params.minBufferFrames + " \n  Selected buffer size (samples): " + MainActivity.params.selectedBufferSize);
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		//Search and set active item to selected buffersize
		int posBuffersize = buffersizes.getPosition(MainActivity.params.selectedBufferSize);
		spBuffersize.setSelection(posBuffersize);


		//The spinner to select the threshold
		spThreshold = (Spinner) fragOvView.findViewById(R.id.spThreshold);
		ArrayAdapter<String> thresholds = new ArrayAdapter<String>(act, android.R.layout.simple_spinner_item, MainActivity.params.thresholds);
		thresholds.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spThreshold.setAdapter(thresholds);
		spThreshold.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				String selected = (String) parent.getItemAtPosition(pos);
				MainActivity.params.thresholdSelected = selected;
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		//Search and set active item to selected threshold
		int posThreshold = thresholds.getPosition(MainActivity.params.thresholdSelected);
		spThreshold.setSelection(posThreshold);


		//spinner for number selection
		spNumberOfTests = (Spinner) fragOvView.findViewById(R.id.spNumberOfTests);
		ArrayAdapter<Integer> numTests = new ArrayAdapter<Integer>(act, android.R.layout.simple_spinner_item, MainActivity.params.allowedTestNumbers);
		numTests.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spNumberOfTests.setAdapter(numTests);
		spNumberOfTests.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				Integer selected = (Integer) parent.getItemAtPosition(pos);
				MainActivity.params.numberOfTests = selected;
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		//Search and set active item to selected number of tests 
		int posNum = numTests.getPosition(MainActivity.params.numberOfTests);
		spNumberOfTests.setSelection(posNum);


		return fragOvView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		updateParamsView();
	}

	@Override 
	public void onDetach() {
		try {
			mp.stop();
			mp.release();
		} catch(Exception e) {}
		mpActive = false;
		super.onDetach();
	}

	/*
	 * Writes systemParameters as formated string to the GUI.
	 * Data has been aquired in the MainActivity class since 
	 * the parameters are needed in the test classes.
	 */
	private void updateParamsView() {
		((TextView)act.findViewById(R.id.twAndroidVersion)).setText("System information: \n" +
				"Android Version: " + MainActivity.params.androidVersion +
				"\n Linux Kernel version: " + MainActivity.params.kernelVersion +
				"\n Architecture: " + MainActivity.params.architecture);
		((TextView)act.findViewById(R.id.twSDKVersion)).setText("SDK: " + MainActivity.params.sdkVersion + 
				"\n Low latency feature supported: " + MainActivity.params.claimsLatencyFeature);

		((TextView)act.findViewById(R.id.twBufferSize)).setText("System frames per buffer: " + MainActivity.params.systemBufferSize);
		((TextView)act.findViewById(R.id.twSampleRate)).setText("Samplerate, native: " + MainActivity.params.defaultSampleRate + ", selected: " + MainActivity.params.sampleRate);
	}
}

