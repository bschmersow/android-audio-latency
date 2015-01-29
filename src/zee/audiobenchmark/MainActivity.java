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
 * 
 * The Icon used for the application has been taken from 
 * "Flurry Extras Icon Pack by The Iconfactory"
 * which is released as freeware for uncommercial use.
 */

package zee.audiobenchmark;


import zee.audiobenchmark.datatypes.SystemParameters;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;

/**
 * Entry point for the application. 
 * The audio benchmark app provides a system overview printout
 * of the devices audio configuration and latency tests
 * for the AudioTrack/AudioRecord class and a native Open SL ES implementation.
 * 
 * @author B. Schmersow
 */
public class MainActivity extends Activity
implements FragmentTaskList.Callbacks {

	//All parameters and settings will be stored here
	public static SystemParameters params;

	//The app is prepared to be adjusted for different devices, 
	//at this time, a two pane is used by default.
	private boolean mTwoPane = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_task_twopane);

		//load active operating system configuration
		params = new SystemParameters();
		retrieveParams();

		//react to clicks on list entries
		((FragmentTaskList) getFragmentManager()
				.findFragmentById(R.id.task_list))
				.setActivateOnItemClick(true);
	}

	/**
	 * Callback method from {@link FragmentTaskList.Callbacks}
	 * indicating that the item with the given ID was selected.
	 */
	@Override
	public void onItemSelected(String id) {
		if (mTwoPane) {
			//id 1: overview
			if(id.equals("1")) {
				FragmentOverview fragmentOV = new FragmentOverview();
				getFragmentManager().beginTransaction()
				.replace(R.id.task_detail_container, fragmentOV)
				.commit();

				//id 2: Standard API
			} else if(id.equals("2")) {
				FragmentAudioTrack fragmentStd = new FragmentAudioTrack();
				getFragmentManager().beginTransaction()
				.replace(R.id.task_detail_container, fragmentStd)
				.commit();

				//id 3: Open SL
			} else if(id.equals("3")) {
				FragmentOpenSL fragmentOpenSL = new FragmentOpenSL();
				getFragmentManager().beginTransaction()
				.replace(R.id.task_detail_container, fragmentOpenSL)
				.commit();
			}
		} 
	}

	/**
	 * Retrieve system information
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void retrieveParams() {
		params.sdkVersion = android.os.Build.VERSION.SDK_INT;
		if(params.sdkVersion >= 17) {
			retrieveAdditionalParams();
		} else {
			params.systemBufferSize = -1;
			params.defaultSampleRate = 44100;
		}
		params.sampleRate = params.defaultSampleRate;
		params.manufacturer = android.os.Build.MANUFACTURER;
		params.deviceName = android.os.Build.MODEL;
		params.kernelVersion = System.getProperty("os.version");
		params.architecture = System.getProperty("os.arch");
		params.androidVersion = android.os.Build.VERSION.RELEASE;

		params.calcAndSetMinBufferSize();

		params.pm = getApplicationContext().getPackageManager();
		params.claimsLatencyFeature = params.pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void retrieveAdditionalParams() {
		AudioManager audioManager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
		String rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
		String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
		params.systemBufferSize =  Integer.parseInt(size);
		params.defaultSampleRate = Integer.parseInt(rate);
	}
}
