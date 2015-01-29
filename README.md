#Welcome to the audioBenchmark app!

##Released under the Apache 2.0 license. The software comes as-is and is currently not supposed to be run as a standalone app, but may be usefull in combination with the ADT

##The latest build may be used by installing audioBenchmark.apk 
###Available Activities: 
* General information (low_latency feature, buffer size, etc.)
* JavaAPI loopback, latency test
* OpenSL loopback, latency test

####Latency tests with the internal require a loopback connection (output directly connected to input). Please be sure to use an attenuator ciruit, otherwise your device may be damaged. For additional information refer to the corresponding Bachelor Thesis at docs.iosight.de

##The repository contains the complete ADT (Eclipse) project package. All needed sources for the full application are within. Building notes:
* Only tested with the ADT (not using Android Studio, since the NDK was not supported at time)
* I recommend to use at least API level 20 (Android 4.4W) for building
* To build the native code, the NDK needs to be installed and configured to include c/c++ files located in /jni. 

The package audioBenchmark.apk may be installed on any Android device that matches the requirements. 
Tested on Android 4.1, 4.2, 4.4.4, L 
Minimum API level: 16, recommended: 19


Shortcut to points of interest:
----------------------------
/src/zee/audioBenchmark/MainActivity.java
Entry point of the application.

/src/zee/audioBenchmark/tasks
The actual implementation of the latency test (-> AudioRecordLatencyTest.java) and other 
threads which will run as asynchronous threads.

/src/zee/audioBenchmark/jni
native code
-> audio-bench-native.c: contains the OpenSL implementation


General information
-------------------
The version used for the evaluation has limited functionality as a stand-alone app.
The OpenSL test do not provide a result output on the activity, system messages
and values for single tests results are not shown in the GUI.
For detailed information of running tests and results, an ADB (Android Debuggin Bridge)
connection (either via LAN or USB) with LogCat output is needed. 


Project Structure 
-------------------
- Generated with Eclipse ADT (Android Developmen Toolkit)
The structure is standardized for development and building with Maven/the ADT

- AndroidManifest.xml
The applications main description file

/assets
unused

/bin
generated binary files

/jni
native code
-> audio-bench-native.c: contains the OpenSL implementation
-> other files are for building and JNI configuration

/libs
necessary libraries and system images for cross-compiling to ARM platform

/obj
generated Java class files

/res
Android application ressources: layout files, audio data, icons

/src
The application's Java source code

Source code structure
----------------------
/src/zee/audioBenchmark (Java package)
Contains the available activities/fragments.

./MainActivity.java
Entry point for the application. Shows the list at the left side and an empty detail fragment.

./FragmentAudioTrack.java
Will be loaded when the "AudioTrack" list entry was selected 

./FragmentOpenSL.java
Will be loaded when the "OpenSL" list entry was selected

./FragmentOverview.java
Will be loaded when the "Overview" list entry was selected

./datatypes
Provides datatypes for list entries, a class for storying the system's setup, and 
a TestResul type for Java class test results (only AudioTrack at this time)

./interfaces
AsyncResponse.java 
May be implemented for asynchronous threads to return results

./tasks
The actual implementation of the latency test (AudioRecordLatencyTest.java) and other 
threads which will run as asynchronous threads.
