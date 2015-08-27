rm -rf obj
ndk-build
rm -rf ../../jniLibs/*
mv libs/* ../../jniLibs/
rm -rf libs

