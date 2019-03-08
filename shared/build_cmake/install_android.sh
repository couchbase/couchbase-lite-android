#!/bin/bash

# x86
adb push ./build_x86/C/tests/C4Tests         		/data/local/tmp/LiteCore/C4Tests
adb push ./build_x86/LiteCore/tests/CppTests 		/data/local/tmp/LiteCore/CppTests
adb push ./build_x86/libLiteCore.so          		/data/local/tmp/LiteCore/libLiteCore.so 
adb push ./build_x86/libLiteCoreREST.so          	/data/local/tmp/LiteCore/libLiteCoreREST.so 
adb push ../../C/tests/data/names_100.json          /data/local/tmp/LiteCore/C/tests/data/names_100.json 
adb push ../../C/tests/data/iTunesMusicLibrary.json /data/local/tmp/LiteCore/C/tests/data/iTunesMusicLibrary.json 
adb push ../../C/tests/data/nested.json             /data/local/tmp/LiteCore/C/tests/data/nested.json 
adb push ../../C/tests/data/states_titlecase.json   /data/local/tmp/LiteCore/C/tests/data/states_titlecase.json 

# Other large test data files
# adb push ~/names_300000.json         /data/local/tmp/LiteCore/C/tests/names_300000.json 
# adb push ~/geoblocks.json            /data/local/tmp/LiteCore/C/tests/geoblocks.json 

# Setup before run C4Tests
# export LD_LIBRARY_PATH=`pwd`
# export ICU_DATA=/system/usr/icu

# Command
# ./C4Tests "[Query]"