
# usage: pass in the name of the avd, eg:
# ./start_android_emulator 3.7WVGA
# you can get the list of avd names by running "android list avd"
emulator64-arm -avd $* -netspeed full -netdelay none
