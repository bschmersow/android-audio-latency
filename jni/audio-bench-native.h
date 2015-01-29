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

#include <assert.h>
#include <jni.h>
#include <android/log.h>

//c std lib
#include <string.h>
#include <limits.h>
#include <errno.h>
#include <math.h>
#include <time.h>
#include <stdbool.h>
//#include <inttypes.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

/*
 * Describes the task that is currently running
 */
typedef enum state {
	init,
	loop,
	latencyTest,
	sineWave
} engineState;


/*
 * Represents a double buffer.
 */
typedef struct doubleBuffer {
	unsigned n; //size
	short* data; //length = 2*n, short = 16bit PCM

	unsigned wp; //write position, 0 or 1
	unsigned rp; //read position, 0 or 1
} dBuf;


/**
 * Initialize the Open SL ES audio engine
 */
void Java_zee_audiobenchmark_FragmentOpenSL_createEngine(JNIEnv* env, jobject obj, int bSize, int sRate, int thresholdDivider);
void Java_zee_audiobenchmark_FragmentOpenSL_createBufferQueueAudioPlayer(JNIEnv* env, jclass clazz);
jboolean Java_zee_audiobenchmark_FragmentOpenSL_createAudioRecorder(JNIEnv* env, jclass clazz);

/**
 * Run an impulse latency tests with given number of tests.
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_latencyTest(JNIEnv* env, jclass clazz, int numberOfTests);

/**
 * Enables a loopback (input directly written to output)
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_nLoopback();

/**
 * Plays a simple generated sine wave
 */
jboolean Java_zee_audiobenchmark_FragmentOpenSL_playSine();

/**
 * Shut down the native audio system
 */
void Java_zee_audiobenchmark_FragmentOpenSL_shutdown(JNIEnv* env, jclass clazz);
