rm -rf obj
ndk-build -C jni
rm -rf ../../jniLibs/*
mv libs/* ../../jniLibs/
rm -rf libs
rm -rf obj

