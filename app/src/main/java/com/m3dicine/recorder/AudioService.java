package com.m3dicine.recorder;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;


final class AudioService {
    private static final String LOG_TAG = AudioService.class.getSimpleName();
    private Utils.STATE state = Utils.STATE.READYTORECORD;

    private Context context;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;

    private String fileName;
    private double lastMax = 100.0d;

    private long start_time = 0;
    private int DATA_COLLECTION_FREQ = 20; //millisecond

    private Boolean running = false;


    final ArrayList<Double> amplitudes; //index, amp

    private Handler mHandler = new Handler();
    private Runnable mPlayProgressExecutor = new Runnable() {
        @Override
        public void run() {
            updatePlayProgress(((AudioCallback)context));
            mHandler.postDelayed(mPlayProgressExecutor, Utils.UI_UPDATE_FREQ);
        }
    };

    AudioService(Context mContext) {
        this.context = mContext;
        amplitudes = new ArrayList<>();

        fileName = Objects.requireNonNull(context.getExternalCacheDir()).getAbsolutePath();
        fileName += "/audiorecordtest.3gp";
    }

    void startRecording(final AudioCallback callback) {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(fileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setMaxDuration(Utils.MAX_TIME);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    //Done recording
                    stopRecording();
                    state = Utils.STATE.READYTOPLAY;
                    callback.onRecordingCompleted();
                }
            }
        });


        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                callback.onRecordingError();
            }
        });

        mRecorder.start();
        start_time = System.currentTimeMillis();
        state = Utils.STATE.RECORDING;

        startUpdateData();
    }

    void stopRecording() {
        if(mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            amplitudes.clear();
        }
        running = false;
        state = Utils.STATE.READYTORECORD;
    }

    private void startUpdateData() {
        running = true;
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (running) {
                    try {
                        int indexTo = (int) ((System.currentTimeMillis() - start_time) / DATA_COLLECTION_FREQ);

                        for (int i = amplitudes.size(); i < indexTo; i++) {
                            amplitudes.add(i, getAmplitudeDb());
                        }

                        Thread.sleep(DATA_COLLECTION_FREQ);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    //ToDo: better amp calculation
    private double getAmplitudeDb() {
        return 20.0d * Math.log10(getAmplitude());
    }

    //ToDo: cleanup
    private double getAmplitude() {
        try {
            double maxAmp = (double) this.mRecorder.getMaxAmplitude();
            if (maxAmp > 2.0d) {
                this.lastMax = maxAmp;
            }
        } catch (Exception e) {
            //Not handled
        }
        return this.lastMax;
    }

    void startPlaying(final AudioCallback callback) {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(fileName);
            mPlayer.setWakeMode(context.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mPlayer.prepare();

            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    stopPlaying();
                    state = Utils.STATE.READYTOPLAY;
                    callback.onPlaybackCompleted();
                }
            });

            mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    stopPlaying();
                    callback.onPlaybackError();
                    return false;
                }
            });
            mPlayer.start();

            mHandler.postDelayed(mPlayProgressExecutor, Utils.UI_UPDATE_FREQ);
            state = Utils.STATE.PLAYING;
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void updatePlayProgress(final AudioCallback callback) {
        callback.onPlaybackProgress(getPlayProgress());
    }

    private int getPlayProgress() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    void stopPlaying() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        state = Utils.STATE.READYTOPLAY;
        mHandler.removeCallbacks(mPlayProgressExecutor);
    }

    Utils.STATE getState() {
        return this.state;
    }
}
