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


/**
 * Data type to contain test results.
 * At this time only used by the AudioRecord test
 */
public class TestResult {

	//unused so far
	public enum apiType {
		audioRecord ("AudioRecord/AudioTrack"),
		soundPool ("SoundPool"), 
		openSL ("NDK: OpenSL");

		private final String name;
		private apiType(String s) {
			name = s;
		}
		public String toString() {
			return name;
		}
	}

	public enum testType {
		impulseLatency ("Impulse measurement");

		private final String name;
		private testType(String s) {
			name = s;
		}
		public String toString() {
			return name;
		}
	}
	public apiType usedApi;
	public testType usedTest;

	//if a test succeeded
	public boolean valid = false;

	//used configuration
	public int bufferSizeInBytes; 
	public int bufferSizeInSamples;
	public int bitdepth;
	public int sampleRateInHz;

	//the results
	public long[] latencyResults;
	public long[] normalizedResults;

	public int average;
	public float stdDeviation;

	private String comments = "";
	private long min, max;

	/**
	 * Constructor with a message output only
	 * (error, timeout, etc.)
	 * @param msg
	 */
	public TestResult(String msg) {
		this.comments = msg;
		valid = false;
	}

	/**
	 * Constructor for a successfully performed test
	 * @param results
	 * @param normalizedResults
	 * @param bufferSizeInSamples
	 * @param bitdepth
	 * @param sampleRateInHz
	 */
	public TestResult(long[] results, long[] normalizedResults, int bufferSizeInSamples, int bitdepth, int sampleRateInHz) {
		//default at this time
		this.usedApi = apiType.audioRecord;
		this.usedTest = testType.impulseLatency;

		this.latencyResults = results;
		this.normalizedResults = normalizedResults;
		this.bufferSizeInSamples = bufferSizeInSamples;
		this.bitdepth = bitdepth;
		this.sampleRateInHz = sampleRateInHz;
		valid = true;
	}

	public String getFormatedTestOutput() {

		String format = "";

		if(valid) {
			checkResults();
			format += "Result for ";
			format += usedApi + " with " + usedTest + "\n";
			format += "Bitrate: " + bitdepth + "\n";
			format += "Samplerate: " + sampleRateInHz + "Hz \n";
			format += "Buffer size: " + bufferSizeInSamples + "smp / " + getBuffersizeInTime() + "ms\n";
			format += "Average latency: " + calcAverage(latencyResults) + "ms \n";
			format += "Max jitter: " + calcMaxJitter(latencyResults) + "ms (" + "min="+ min+",max=" + max + ")\n";
			format += "Standard deviation: " + this.stdDeviation + "\n";
			format += "Average normalized latency: " + calcAverage(normalizedResults) + "ms \n";
			format += "Max jitter for normalized values: " + calcMaxJitter(normalizedResults) + "ms (" + "min="+ min+",max=" + max + ")\n";
			format += "Number of test: " + latencyResults.length + "\n";
		}
		format += comments;
		return format;
	}

	private void checkResults() {
		long av = calcAverage(latencyResults);
		if(av == 0) comments += "ERROR: No valid signal received, check connections\n";
		if(av < 10 || av > 400) comments += "WARNING: Value out of expected range, check connections\n";
		if(calcMaxJitter(latencyResults) > av/2) comments += "WARNING: Jitter out of expected range, (at least one) result may be invalid\n";
	}

	private int getBuffersizeInTime() {
		if(sampleRateInHz == 0 || bufferSizeInSamples == 0) return 0;
		return bufferSizeInSamples / (sampleRateInHz / 1000);
	}

	private int calcMaxJitter(long[] results) {
		min=Long.MAX_VALUE;
		max=Long.MIN_VALUE;
		if(results == null) {
			return 0;
		}
		for(long l:results) {
			if(l < min) min = l;
			if(l > max) max = l;
		}
		return (int) (max-min);
	}

	private long calcAverage(long[] results) {
		if(results == null) {
			return 0;
		}
		long sum = 0;

		//mean average
		for(long l:results) {
			sum += l;
		}
		sum = sum/results.length;
		long avgL = sum;
		this.average = (int) sum;

		double variance = 0.0;
		//sample standard deviation
		for(long l:results) {
			variance += (l-avgL)*(l-avgL);
		}
		variance = variance/(results.length-1);
		this.stdDeviation = (float) Math.sqrt(variance);


		return sum;
	}
}
