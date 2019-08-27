package com.m3dicine.recorder;

public interface AudioCallback {
    void onRecordingCompleted();
    void onRecordingError();

    void onPlaybackProgress(int time);
    void onPlaybackCompleted();
    void onPlaybackError();
}

