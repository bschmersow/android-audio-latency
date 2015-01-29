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

package zee.audiobenchmark.datatypes;

import java.util.ArrayList;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;

/**
 * In this class, all global parameters are defined
 */
public class SystemParameters {

	//Device information
	public int sdkVersion;
	public String kernelVersion;
	public String architecture;
	public String androidVersion;
	public String manufacturer;
	public String deviceName;
	public PackageManager pm;
	public boolean claimsLatencyFeature;

	// Tests are only working with PCM 16 bit encoding!
	public static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

	// Sample Rate settings
	public ArrayList<String> availableSamplerates = new ArrayList<String>();
	public int defaultSampleRate; //sample rate in Hz
	public int sampleRate; //chosen sample rate

	// Buffer size settings
	public ArrayList<String> bufferSizes = new ArrayList<String>();
	public String selectedBufferSize; //Slection in overview
	public int systemBufferSize; //buffer in frames, refers to the system's buffer size
	public int minBufferBytes; //minimum buffer size to successfully create an AudioTrack instance
	public int minBufferFrames;

	// Latency test config
	public ArrayList<String> thresholds = new ArrayList<String>();
	public String thresholdSelected;
	private final String tHigh="high", tMed="medium", tLow="low";
	private final String thresholdDefault = tHigh;
	public Integer[] allowedTestNumbers = new Integer[]{5, 10, 25, 50, 100, 250, 500, 1000};
	public int numberOfTests = 10; //number of impulses to measure


	/**
	 * Constructor
	 */
	public SystemParameters() {
		checkAvailableSamplerates();
		thresholds.add(tHigh);
		thresholds.add(tMed);
		thresholds.add(tLow);
		thresholdSelected = thresholdDefault;


	}

	public String toString() {
		String format = "---- ----\n";
		format += "Manufacturer: " + manufacturer + " | model: " + deviceName + "\n";
		format += "Android version " + androidVersion + "| API level " + sdkVersion + " | Linux kernel " + kernelVersion + "\n";
		format += "Low latency feature claimed: " + claimsLatencyFeature + " | System buffer frames: " + systemBufferSize + "\n";
		format += "---- ----\n";
		return format;
	}

	/**
	 * Returns the value by which the max PCM sample value will be divided
	 * depending on selection in overview
	 */
	public int getThresholdDivider() {
		int high = 5; //SHRT_MAX / 5 
		int medium = 20;
		int low = 200;
		if(thresholdSelected.equals(tHigh)) return high;
		if(thresholdSelected.equals(tMed)) return medium;
		if(thresholdSelected.equals(tLow)) return low;
		return high; //default
	}

	/**
	 * The minimum buffer size may change depending on selected sample rate
	 * @return min buffer size in bytes
	 */
	public int calcAndSetMinBufferSize() {
		minBufferBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		if(systemBufferSize == -1) systemBufferSize = minBufferBytes/2; //on API16: no system frames can be retrieved, set default
		minBufferFrames = minBufferBytes/2;
		setBuffersizes();
		return minBufferBytes;
	}

	/**
	 * Retrieve available samplerates 
	 */
	private void checkAvailableSamplerates() {
		for(int sRate : new int[]{8000, 11025, 16000, 22050, 44100, 48000, 96000}) {
			int bSize = -1;
			//if the min buffer size can be retrieved, the samplerate is valid
			try {
				bSize = AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			} catch (Exception e) {}
			if(bSize > 0) {
				availableSamplerates.add(Integer.toString(sRate));
			}
		}
	}

	/**
	 * Adds buffersizes which are a multiple of the system buffer size to spinner
	 * Min buffer size is selected by default
	 */
	private void setBuffersizes() {
		bufferSizes.clear();
		for(int i=1; i<8; i++) {
			bufferSizes.add(Integer.toString(i*systemBufferSize));
		}
		if(!bufferSizes.contains(minBufferFrames)) {
			bufferSizes.add(Integer.toString(minBufferFrames)); //PCM 16bit: 1frame = 2B
		}
		selectedBufferSize = Integer.toString(minBufferFrames);
	}
}
