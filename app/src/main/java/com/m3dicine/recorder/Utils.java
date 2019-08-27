package com.m3dicine.recorder;

final class Utils {

    public enum STATE {
        READYTORECORD,
        RECORDING,
        READYTOPLAY,
        PLAYING
    }


    static int MAX_TIME = 20000; //millisecond
    static int UI_UPDATE_FREQ = 50; //millisecond

    static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

}
