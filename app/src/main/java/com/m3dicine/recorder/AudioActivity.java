package com.m3dicine.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;

import java.util.Locale;


public class AudioActivity extends AppCompatActivity implements AudioCallback {
    private static final String LOG_TAG = AudioActivity.class.getSimpleName();
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
    private boolean permissionToRecord = false;
    private int displayWidth;


    private AudioService audioService;
    private WaveChart mChart = new WaveChart();

    private TextView    textStatus = null;
    private Button      buttonTopStatus = null;
    private LineChart   viewChartAudio = null;
    private TextView    playCounter = null;
    private View        playHead = null;
    private ImageButton buttonRecordPlay = null;
    private Button      buttonBottom = null;

    public int countdownCounter = Utils.MAX_TIME / 1000;
    private CountDownTimer timer = null;

    private int usedIndex = 0;
    private Handler mHandler = new Handler();


    private Runnable mTickExecutor = new Runnable() {
        @Override
        public void run() {
            tick();
            mHandler.postDelayed(mTickExecutor, Utils.UI_UPDATE_FREQ);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioService = new AudioService(this);
        ActivityCompat.requestPermissions(this, permissions, Utils.REQUEST_RECORD_AUDIO_PERMISSION);

        textStatus          = findViewById(R.id.tv_view_name);
        buttonTopStatus     = findViewById(R.id.bt_status);
        viewChartAudio      = findViewById(R.id.chart_audio);
        playCounter         = findViewById(R.id.tv_play_counter);
        playHead            = findViewById(R.id.v_playhead);
        buttonRecordPlay    = findViewById(R.id.bt_recordplay);
        buttonBottom        = findViewById(R.id.bt_bottom);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;

        buttonRecordPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (audioService.getState()) {
                    case READYTORECORD:
                        startRecording();
                        break;

                    case RECORDING:
                        stopRecording();
                        break;

                    case READYTOPLAY:
                        startPlaying();
                        break;

                    case PLAYING:
                        stopPlaying();
                        break;

                    default:
                        Log.e(LOG_TAG, "Wrong state");
                }
            }
        });

        buttonBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (audioService.getState()) {
                    case READYTOPLAY:
                    case PLAYING:
                        stopPlaying();
                        stopRecording();
                        break;

                    case RECORDING:
                    case READYTORECORD:
                    default:
                        //Do nothing
                }
            }
        });

        mChart.setupChart(viewChartAudio);
    }

    private void startRecording() {
        buttonRecordPlay.setBackground(getDrawable(R.drawable.stop));
        buttonBottom.setEnabled(false);

        audioService.startRecording(this);
        mHandler.postDelayed(mTickExecutor, Utils.UI_UPDATE_FREQ);
        uiCountdownTimer();
    }

    private void stopRecording() {
        textStatus.setText(R.string.recording_view);
        buttonRecordPlay.setBackground(getDrawable(R.drawable.record));
        buttonTopStatus.setText(R.string.ready);
        buttonTopStatus.setVisibility(View.VISIBLE);
        playCounter.setVisibility(View.GONE);
        buttonBottom.setText(R.string.playback_now);
        buttonBottom.setEnabled(false);

        viewChartAudio.clearValues();
        mChart.setupChart(viewChartAudio);
        audioService.stopRecording();

        usedIndex = 0;
        countdownCounter = Utils.MAX_TIME / 1000;
        mHandler.removeCallbacks(mTickExecutor);
        timer.cancel();
    }

    @Override
    public void onRecordingCompleted() {
        Log.d(LOG_TAG, "onRecordingCompleted");

        textStatus.setText(R.string.playback_view);
        playCounter.setVisibility(View.VISIBLE);
        playCounter.setText(String.format(Locale.getDefault(), "%.2f s", 0.00f));
        buttonRecordPlay.setBackground(getDrawable(R.drawable.play));
        buttonTopStatus.setVisibility(View.INVISIBLE);
        buttonTopStatus.setText(R.string.ready);
        buttonBottom.setText(R.string.back_rec);
        buttonBottom.setEnabled(true);
    }

    @Override
    public void onRecordingError() {
        Log.e(LOG_TAG, "Error recording media");
    }

    private void startPlaying() {
        buttonRecordPlay.setBackground(getDrawable(R.drawable.stop));
        playCounter.setVisibility(View.VISIBLE);
        playHead.setVisibility(View.VISIBLE);
        audioService.startPlaying(this);
    }

    private void stopPlaying() {
        buttonRecordPlay.setBackground(getDrawable(R.drawable.play));
        playCounter.setVisibility(View.VISIBLE);
        playCounter.setText(String.format(Locale.getDefault(), "%.2f s", 0.00f));
        playHead.setVisibility(View.GONE);
        playHead.setTranslationX(0);
        audioService.stopPlaying();
    }

    @Override
    public void onPlaybackProgress(int currentTime) {
        Log.d(LOG_TAG, "onPlaybackProgress");

        playCounter.setText(String.format(Locale.getDefault(), "%.2f s", (currentTime / 1000f)));
        playHead.setTranslationX(currentTime * (displayWidth / (float) Utils.MAX_TIME));
    }

    @Override
    public void onPlaybackCompleted() {
        Log.d(LOG_TAG, "onPlaybackCompleted");
        stopPlaying();
    }

    @Override
    public void onPlaybackError() {
        Log.e(LOG_TAG, "Error playing media");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioService.stopRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Utils.REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecord) {
            Toast.makeText(this, "No permission to record", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void tick() {
        if (audioService.getState() == Utils.STATE.RECORDING) {
            int currentIndex = audioService.amplitudes.size();


            //Log.d("Plotting: ","used: "+ usedIndex + ", current: " + currentIndex + ", " + viewChartAudio.getData().getDataSetCount());
            for (int i = usedIndex; i < currentIndex; i++) {
                double amplitude = audioService.amplitudes.get(i);
                //Log.d("Voice Recorder: ","amplitude: "+ amplitude + ", " + (current_time - start_time));
                mChart.addEntry(i, (float) amplitude - 10.0f, 0); //first dataset
                mChart.addEntry(i, (float) -amplitude + 10.0f, 1); //second dataset
            }
            usedIndex = currentIndex;
        }
    }

    private void uiCountdownTimer() {
        timer = new CountDownTimer(Utils.MAX_TIME, 1000) {
            public void onTick(long millisUntilFinished) {
                buttonTopStatus.setText(String.valueOf(countdownCounter));
                countdownCounter--;
            }

            public void onFinish() {

            }
        }.start();
    }
}
