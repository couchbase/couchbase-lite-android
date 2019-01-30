// This header just works around the problem that, on OS X, the "#include <jni.h>" emitted by javah
// doesn't work, because jni.h is in the JavaVM framework.
#ifdef APPLE
#include <JavaVM/jni.h>
#endif