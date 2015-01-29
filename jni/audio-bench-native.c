/*
 * Copyright 2014 B.Schmersow
 * The following code is partially based on the code sample
 * "nativeAudio" included in the NDK package,
 * licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <audio-bench-native.h>

/**
 * @Note: All variables and functions not needed outside this file
 * are declared static to spare the global namespace.
 */

static engineState state = init;
static jboolean loopInit;
static jboolean loopActive;
static jboolean testActive;

//local variables
static void* null_ptr = (void*)0;
static unsigned sampleRateInHz;
static int sampleRateinmHz; //Open SL uses mHz
static const char LOG_TAG[] = "audio-bench-native.c";

//Sine wave
#define SIN_BUFFER_SAMPLES 256
static short sineBuffer[SIN_BUFFER_SAMPLES];

//Impulses
static int numTests;
static int padding = 0;
static unsigned basePadding;
static unsigned impRec;
static int* lResults;
static int* lResultsNorm;
static int64_t timeStamp;

//threshold for impulse recognition, will be set on creation to selection
static short imp_threshold = SHRT_MAX;

// pointer and size of the next player buffer to enqueue, and number of remaining buffers
static unsigned bufferSize;
static short* nextPlayBuffer;
static short* nextRecBuffer;
static unsigned nextPlaySize;
static unsigned nextRecSize;
static int nextCount;

// engine objects
static SLObjectItf engineObject;
static SLEngineItf engine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
static SLEffectSendItf bqPlayerEffectSend;
static SLMuteSoloItf bqPlayerMuteSolo;
static SLVolumeItf bqPlayerVolume;

// recorder interfaces
static SLObjectItf recorderObject = NULL;
static SLRecordItf recorderRecord;
static SLAndroidSimpleBufferQueueItf recorderBufferQueue;

//double buffers
static dBuf* inBuffer;
static dBuf* outBuffer;
static jboolean initLoop;
static jboolean initTest;

//forward declaration needed
static void Java_zee_audiobenchmark_FragmentOpenSL_initSineWaveBuffer();
static void Java_zee_audiobenchmark_logFormatedResult();

/**
 * Functions to handle access to the double buffers
 * total size will be 2*len
 */
static void dBuf_init(dBuf* buf, int len) {
	assert(len>16 && len<8192); //min and max value
	buf->data = calloc(2*len, sizeof(short));
	buf->n = len;
	buf->wp = 0;
	buf->rp = 0;
}

static inline unsigned dBuf_size(dBuf* buf) {
	return (buf->n * sizeof(short));
}

/*
 * Returns the next position (buffer half) to read
 */
static short* dBuf_getNextRead(dBuf* buf) {
	assert(buf->rp <= 1);
	if(buf->rp == 0) {
		buf->rp = 1;
		return buf->data;
	} else {
		buf->rp = 0;
		return &buf->data[buf->n];
	}
	return null_ptr;
}

/*
 * Returns the next position (buffer half) to write
 * If running properly - after the first cicle -
 * the reading and writing position should never be the same
 */
static short* dBuf_getNextWrite(dBuf* buf) {
	assert(buf->wp <= 1);
	if(buf->wp == 0) {
		buf->wp = 1;
		return buf->data;
	} else {
		buf->wp = 0;
		return &buf->data[buf->n];
	}
	return null_ptr;
}

static void dBuf_reset(dBuf* buf) {
	buf->wp = 0;
	buf->rp = 0;
	unsigned i;
	assert(sizeof(buf->data) == (buf->n * 2 * sizeof(short)));
	for(i=0; i<(buf->n * 2); i++) {
		buf->data[i] = 0;
	}
}

static void dBuf_destroy(dBuf* buf) {
	free(buf->data);
	free(buf);
}//end: double buffer helping functions

/*
 * Aquire current timestamp in milliseconds
 */
static int64_t getNsTimestamp() {
	struct timespec stamp;
	clock_gettime(CLOCK_MONOTONIC, &stamp);
	int64_t nsec = (int64_t) stamp.tv_sec*1000000000LL + stamp.tv_nsec;
	return nsec;
}

/*
 * normalizes a timing result to the maximum
 * dependend on the buffer position (worst-case simulation)
 */
static int normalizeResult(int val, int pos) {
	//calculate time for a buffersize of size pos
	//this simulates that the impulse was received at position 0
	int elapsed = (pos) / (sampleRateInHz/1000); //in ms
	return (val + elapsed);
}

/*
 * Stop recording and playback, clear for next task
 */
static void stopEngine() {
	SLresult result;
	// set the player's state to stopped
	result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	//clear recorder state
	result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_STOPPED);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;
	result = (*recorderBufferQueue)->Clear(recorderBufferQueue);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	//reset buffers
	dBuf_reset(inBuffer);
	dBuf_reset(outBuffer);
}

/*
 * May be used for reading results from Java classes
 * (not yet implemented)
 */
int Java_zee_audiobenchmark_FragmentOpenSL_getNumResults() {
	return -1;
}
int Java_zee_audiobenchmark_FragmentOpenSL_getNextResult() {
	return -1;
}

/*
 * Initialize OpenSL audio engine
 * @params: JNIEnv* env, jclass clazz: JNI parameters
 * int bSize preferred buffersize in samples
 * int sRate preferred samplerate in Hz
 */
void Java_zee_audiobenchmark_FragmentOpenSL_createEngine(JNIEnv* env, jobject obj, int bSize, int sRate, int thresholdDivider) {

	//adjust local values to given parameters
	bufferSize = bSize;
	sampleRateInHz = 44100; //sRate;
	imp_threshold = SHRT_MAX / thresholdDivider;
	switch(sRate) {
	case 44100:
		sampleRateinmHz = SL_SAMPLINGRATE_44_1;
		break;
	case 48000:
		sampleRateinmHz = SL_SAMPLINGRATE_48;
		break;
	case 16000:
		sampleRateinmHz = SL_SAMPLINGRATE_16;
		break;
	case 8000:
		sampleRateinmHz = SL_SAMPLINGRATE_8;
		break;
	default:
		__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Unsupported sample rate requested, defaulting to 44.1kHz");
		sampleRateinmHz = SL_SAMPLINGRATE_44_1;
		break;
	}

	//init buffers
	state = init;
	inBuffer = malloc(sizeof(dBuf));
	outBuffer = malloc(sizeof(dBuf));

	assert(inBuffer != null_ptr && outBuffer != null_ptr);

	dBuf_init(inBuffer, bufferSize);
	dBuf_init(outBuffer, bufferSize);
	Java_zee_audiobenchmark_FragmentOpenSL_initSineWaveBuffer();

	//init Open SL engine
	SLresult result;
	// instantiate engine with default configuration
	result = slCreateEngine(&engineObject, 0, null_ptr, 0, null_ptr, null_ptr);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// initialize engine (=realize)
	result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// get the engine interface, which is needed in order to create other objects
	result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engine);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	const SLInterfaceID ids[] = {};
	const SLboolean req[] = {};
	result = (*engine)->CreateOutputMix(engine, &outputMixObject, 0, ids, req);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// realize the output mix
	result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;
}

// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
	assert(bq == bqPlayerBufferQueue);
	assert(NULL == context);
	SLresult result;

	switch(state) {
	case latencyTest:
		if(--padding > 0) {
			//set next playback buffer
			nextPlaySize = dBuf_size(outBuffer);
			nextPlayBuffer = dBuf_getNextRead(outBuffer);
			nextPlayBuffer[0] = 0; //clear impulse if set

			//enqueue for playback
			result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
			assert(SL_RESULT_SUCCESS == result);
			(void)result;
		}
		else {
			if (nextCount-- > 0) {
				//set next playback buffer
				nextPlaySize = dBuf_size(outBuffer);
				nextPlayBuffer = dBuf_getNextRead(outBuffer);

				//write impulse to out buffer
				nextPlayBuffer[0] = SHRT_MAX;

				//measurement start time
				timeStamp = getNsTimestamp();

				//enqueue for playback
				result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
				assert(SL_RESULT_SUCCESS == result);
				(void)result;

				// reset padding
				padding = basePadding;
			} else {
				unsigned i;
				__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Latency test finished. Result:");
				Java_zee_audiobenchmark_logFormatedResult();
				//test output done
				stopEngine();
				free(lResults);
				free(lResultsNorm);
				lResults = null_ptr;
				lResultsNorm = null_ptr;
				state = init;
			}
		}
		break;
	case loop:
		//set next playback buffer
		nextPlaySize = dBuf_size(outBuffer);
		nextPlayBuffer = dBuf_getNextRead(outBuffer);

		//enqueue for playback
		result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
		assert(SL_RESULT_SUCCESS == result);
		(void)result;
		break;
	case sineWave:
		if (--nextCount > 0) {
			result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
			assert(SL_RESULT_SUCCESS == result);
			(void)result;
		} else {
			stopEngine();
			state = init;
		}
		break;
	case init:
		//can only be reached if state has been reset but still buffers are enqueued
		stopEngine();
		break;
	default:
		stopEngine();
		state = init;
		break;
	}
}

// this callback handler is called every time a buffer finishes recording
void bqRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
	SLresult result;
	assert(bq == recorderBufferQueue);
	assert(NULL == context);

	unsigned i;
	short* recordedHalf;
	short* processingHalf;

	switch(state) {
	case latencyTest:
		//Recording on one buffer-half has finished, retrieve position for reading
		recordedHalf = dBuf_getNextRead(inBuffer);

		//check buffer for impulse
		for(i=0;i<inBuffer->n;i++) {
			if(recordedHalf[i] > imp_threshold) {
				if(impRec < numTests) {
					int64_t now = getNsTimestamp();
					int lResult = (int)((now - timeStamp)/1000000);
					lResults[impRec] = lResult;
					lResultsNorm[impRec] = normalizeResult(lResult, i);
					__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "rec impulse, time: %d ms; normalized: %d", lResults[impRec], lResultsNorm[impRec]);
					impRec++;
					break;
				}
			}
		}
		//switch to next recording buffer
		nextRecBuffer = dBuf_getNextWrite(inBuffer);
		nextRecSize = dBuf_size(inBuffer);
		result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer, nextRecSize);

		if(initTest) {
			//We start with an empty buffer, the impulse will be enqueued in callback after padding
			//set next playback buffer
			nextPlaySize = dBuf_size(outBuffer);
			nextPlayBuffer = dBuf_getNextRead(outBuffer);

			//enqueue for playback
			result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
			assert(SL_RESULT_SUCCESS == result);
			(void)result;
			nextPlaySize = dBuf_size(outBuffer);
			nextPlayBuffer = dBuf_getNextRead(outBuffer);
			initTest = JNI_FALSE;
		}
		break;
	case loop:
		//Recording on one bufferhalf has finished, copy data to outBuffer
		recordedHalf = dBuf_getNextRead(inBuffer);	//pos of the finished data in the recording buffer

		processingHalf = dBuf_getNextWrite(outBuffer);		//pos in outBuffer for playback
		for(i=0;i<inBuffer->n;i++) {
			//normally, here would be the processing part.
			//since we do none, its simply copying
			nextPlayBuffer[i] = recordedHalf[i];
		}

		//switch to next recording buffer
		nextRecBuffer = dBuf_getNextWrite(inBuffer);
		nextRecSize = dBuf_size(inBuffer);
		result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer,
				nextRecSize);

		//init playback if not done yet
		if(initLoop) {
			// set the player's state to playing
			result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
			assert(SL_RESULT_SUCCESS == result);
			(void)result;
			initLoop = JNI_FALSE;
		}
		break;
	case init:
		break;
	default:
		break;
	}
}

// create buffer queue audio player
void Java_zee_audiobenchmark_FragmentOpenSL_createBufferQueueAudioPlayer(JNIEnv* env, jclass clazz) {
	SLresult result;

	// configure audio source
	SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
	SLDataFormat_PCM format_pcm = {
			SL_DATAFORMAT_PCM,
			1,
			sampleRateinmHz,
			SL_PCMSAMPLEFORMAT_FIXED_16,
			SL_PCMSAMPLEFORMAT_FIXED_16,
			SL_SPEAKER_FRONT_CENTER,
			SL_BYTEORDER_LITTLEENDIAN
	};
	SLDataSource audioSrc = {&loc_bufq, &format_pcm};

	// configure audio sink
	SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
	SLDataSink audioSnk = {&loc_outmix, NULL};

	// create audio player
	const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
	const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
	result = (*engine)->CreateAudioPlayer(engine, &bqPlayerObject, &audioSrc, &audioSnk,
			2, ids, req);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// realize the player
	result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// get the play interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// get the buffer queue interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
			&bqPlayerBufferQueue);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// register callback on the buffer queue
	result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// get the volume interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;
}

// create audio recorder
jboolean Java_zee_audiobenchmark_FragmentOpenSL_createAudioRecorder(JNIEnv* env, jclass clazz) {
	SLresult result;

	// configure audio source
	SLDataLocator_IODevice loc_dev = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOINPUT,
			SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
	SLDataSource audioSrc = {&loc_dev, NULL};

	// configure audio sink
	SLDataLocator_AndroidSimpleBufferQueue loc_bq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
	SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, sampleRateinmHz,
			SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
			SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};
	SLDataSink audioSnk = {&loc_bq, &format_pcm};

	// create audio recorder
	// (requires the RECORD_AUDIO permission)
	const SLInterfaceID id[1] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
	const SLboolean req[1] = {SL_BOOLEAN_TRUE};
	result = (*engine)->CreateAudioRecorder(engine, &recorderObject, &audioSrc,
			&audioSnk, 1, id, req);
	if (SL_RESULT_SUCCESS != result) {
		return JNI_FALSE;
	}

	// realize the audio recorder
	result = (*recorderObject)->Realize(recorderObject, SL_BOOLEAN_FALSE);
	if (SL_RESULT_SUCCESS != result) {
		return JNI_FALSE;
	}

	// get the record interface
	result = (*recorderObject)->GetInterface(recorderObject, SL_IID_RECORD, &recorderRecord);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// get the buffer queue interface
	result = (*recorderObject)->GetInterface(recorderObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
			&recorderBufferQueue);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// register callback on the buffer queue
	result = (*recorderBufferQueue)->RegisterCallback(recorderBufferQueue, bqRecorderCallback,
			NULL);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	return JNI_TRUE;
}

/**
 * Initializes the impulse latency tests.
 * The actual test routines are within the Player/Recorder callbacks.
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_latencyTest(JNIEnv* env, jclass clazz, int numberOfTests) {

	numTests = numberOfTests;

	if(lResults != null_ptr) free(lResults);
	if(lResultsNorm != null_ptr) free(lResultsNorm);
	lResults = calloc(numTests, sizeof(int));
	lResultsNorm = calloc(numTests, sizeof(int));

	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Starting latency test with Open SL ES, (threshold: %d) please wait...", imp_threshold);
	initTest = JNI_TRUE;

	// stop recording/playback and clear buffer queue
	stopEngine();
	/*
	 * Prepare impulse playback.
	 * The playback is initiated after the first recorder buffer has been filled.
	 */
	state = latencyTest;
	impRec = 0;
	SLresult result;

	basePadding = (sampleRateInHz/bufferSize); //~0.5s
	if(basePadding < 10 || basePadding > 1000) {
		basePadding = 20;
	}
	padding = basePadding;
	nextCount = numTests;

	/*
	 * Prepare recording
	 */
	//register 2 buffers
	nextRecBuffer = dBuf_getNextWrite(inBuffer);
	nextRecSize = dBuf_size(inBuffer);
	result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer,
			nextRecSize);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	nextRecBuffer = dBuf_getNextWrite(inBuffer);
	result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer,
			nextRecSize);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// start recording
	result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_RECORDING);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// set the player's state to playing
	result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	//We start with an empty buffer, the impulse will be enqueued in callback after padding
	//set next playback buffer
	nextPlaySize = dBuf_size(outBuffer);
	nextPlayBuffer = dBuf_getNextRead(outBuffer);

	return JNI_TRUE;
}

/*
 * Plays the sinewave that has been synthesized in initSineWaveBuffer()
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_playSine() {

	stopEngine();
	state = sineWave;

	//set next buffer to enqueue to sine wave buffer
	nextPlayBuffer = sineBuffer;
	nextPlaySize = sizeof(sineBuffer);

	padding = 0;

	SLresult result;

	int seconds = 2;
	int totalSamples = seconds * sampleRateInHz;
	nextCount = totalSamples/SIN_BUFFER_SAMPLES;

	//enqueue 2 buffers
	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
	if (SL_RESULT_SUCCESS != result) {
		return JNI_FALSE;
	}
	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
	if (SL_RESULT_SUCCESS != result) {
		return JNI_FALSE;
	}

	// set the player's state to playing
	result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	return JNI_TRUE;
}


/**
 * Enables a loopback (input directly written to output)
 * The loopback mode can be used to test for xruns.
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_nLoopback() {
	SLresult result;
	state = loop;
	initLoop = JNI_TRUE;
	/*
	 * Prepare recording
	 */
	// in case already recording, stop recording and clear buffer queue
	stopEngine();

	// enqueue two buffers to be filled by the recorder
	// using the double buffer method
	nextRecBuffer = dBuf_getNextWrite(inBuffer);
	nextRecSize = dBuf_size(inBuffer);
	result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer,
			nextRecSize);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	nextRecBuffer = dBuf_getNextWrite(inBuffer);
	result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, nextRecBuffer,
			nextRecSize);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	// start recording
	result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_RECORDING);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;

	//set next playback buffer
	nextPlaySize = dBuf_size(outBuffer);
	nextPlayBuffer = dBuf_getNextRead(outBuffer);

	//enqueue for playback, play buffer is set in recording callback
	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextPlayBuffer, nextPlaySize);
	assert(SL_RESULT_SUCCESS == result);
	(void)result;
	return JNI_TRUE;
}

void Java_zee_audiobenchmark_FragmentOpenSL_resetEngine(JNIEnv* env, jclass clazz) {
	stopEngine();
	state = init;
}

// shut down the native audio system
void Java_zee_audiobenchmark_FragmentOpenSL_shutdown(JNIEnv* env, jclass clazz) {

	// destroy buffer queue audio player object, and invalidate all associated interfaces
	if (bqPlayerObject != NULL) {
		(*bqPlayerObject)->Destroy(bqPlayerObject);
		bqPlayerObject = NULL;
		bqPlayerPlay = NULL;
		bqPlayerBufferQueue = NULL;
		bqPlayerEffectSend = NULL;
		bqPlayerMuteSolo = NULL;
		bqPlayerVolume = NULL;
	}

	// destroy audio recorder object, and invalidate all associated interfaces
	if (recorderObject != NULL) {
		(*recorderObject)->Destroy(recorderObject);
		recorderObject = NULL;
		recorderRecord = NULL;
		recorderBufferQueue = NULL;
	}

	// destroy output mix object, and invalidate all associated interfaces
	if (outputMixObject != NULL) {
		(*outputMixObject)->Destroy(outputMixObject);
		outputMixObject = NULL;
	}

	// destroy engine object, and invalidate all associated interfaces
	if (engineObject != NULL) {
		(*engineObject)->Destroy(engineObject);
		engineObject = NULL;
		engine = NULL;
	}

	dBuf_destroy(inBuffer);
	dBuf_destroy(outBuffer);
	if(lResults != null_ptr) free(lResults);
	if(lResultsNorm != null_ptr) free(lResultsNorm);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Native audio engine shut down.");
}

/*
 * Calculate average and standard deviation
 * @parameters:
 * pointers: return values
 *
 * @return: number of valid values
 */
static int calcAverage(int results[], int len, int* avg, int* min, int* max, float* stdDeviation) {
	unsigned i;
	int sum=0;
	int numR = 0;
	double squSum = 0;

	*avg = 0;
	*stdDeviation = 0.0f;
	*min = INT_MAX;
	*max = INT_MIN;

	//calculate mean average
	for(i=0; i<len; i++) {
		int val = results[i];
		//values out of range
		if(val < 10 || val > 500) continue;

		sum += val;
		squSum = (double) (val*val);
		if(val < *min) *min = val;
		if(val > *max) *max = val;
		numR ++;
	}

	assert(numR > 0);
	if(numR < 1) return -1;
	*avg = sum/numR;

	double variance = 0.0;
	double average = (double) *avg;

	//Sample standard deviation
	for(i=0; i<len; i++) {
		double val = (double)results[i];
		if(val < 10.0 || val > 500.0) continue;
		variance += (val-average)*(val-average);
	}
	variance = variance/(numR-1);
	*stdDeviation = (float)sqrt(variance);

	return numR;
}

/*
 * Write a result to the LogCat output
 */
static void Java_zee_audiobenchmark_logFormatedResult() {
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Result for Open SL latency test:");
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Bitrate: 16 bit \n Samplerate: %d Hz \n", sampleRateInHz);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Buffer size: %d smp, %f ms", bufferSize, ((float)bufferSize/sampleRateInHz)*1000);

	int min;
	int max;
	int avg;
	float stdDeviation;

	//for initial results
	int numR = calcAverage(lResults, impRec, &avg, &min, &max, &stdDeviation);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Average latency: %d ms", avg);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Standard deviation: %.2f", stdDeviation);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "(min/max: %d ms / %d ms)", min, max);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Number of valid values: %d", numR);

	//for normalized results
	numR = calcAverage(lResultsNorm, impRec, &avg, &min, &max, &stdDeviation);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Average normalized latency: %d ms", avg);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "(min/max: %d ms / %d ms)", min, max);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Number of valid values: %d", numR);
}

/*
 * Synthesize a sine wave
 */
static void Java_zee_audiobenchmark_FragmentOpenSL_initSineWaveBuffer() {
	// synthesize a mono sine wave and place it into a buffer
	unsigned i;
	double f = sampleRateInHz / SIN_BUFFER_SAMPLES; //chosen such that it fits into the buffer
	for(i = 0; i < SIN_BUFFER_SAMPLES; i++) {
		sineBuffer[i] = SHRT_MAX * sin(f/(double)sampleRateInHz * (double)i * 2 * M_PI);
	}
}
