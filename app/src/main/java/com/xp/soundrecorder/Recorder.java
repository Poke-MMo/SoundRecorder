package com.xp.soundrecorder;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class Recorder implements OnCompletionListener, OnErrorListener {
    private static final String TAG = "Recorder";
    private static final String SAMPLE_PREFIX = "recording";

    public static final String SAMPLE_DEFAULT_DIR = "/sound_recorder";

    public static final int IDLE_STATE = 0;

    public static final int RECORDING_STATE = 1;

    public static final int PLAYING_STATE = 2;

    public static final int PLAYING_PAUSED_STATE = 3;

    private int mState = IDLE_STATE;

    public static final int NO_ERROR = 0;

    public static final int STORAGE_ACCESS_ERROR = 1;

    public static final int INTERNAL_ERROR = 2;

    public static final int IN_CALL_RECORD_ERROR = 3;

    public interface OnStateChangedListener {
        void onStateChanged(int state);

        void onError(int error);
    }

    private Context mContext;

    private OnStateChangedListener mOnStateChangedListener = null;

    private long mSampleStart = 0;

    private int mSampleLength = 0;

    private File mSampleFile = null;

    private File mSampleDir;

    private MediaPlayer mPlayer = null;

    public Recorder(Context context) {
        mContext = context;
        File sampleDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + SAMPLE_DEFAULT_DIR);
        if (!sampleDir.exists()) {
            sampleDir.mkdirs();
        }
        mSampleDir = sampleDir;

        syncStateWithService();
    }

    public boolean syncStateWithService() {
        if (RecorderService.isRecording()) {
            mState = RECORDING_STATE;
            mSampleStart = RecorderService.getStartTime();
            mSampleFile = new File(RecorderService.getFilePath());
            return true;
        } else if (mState == RECORDING_STATE) {
            return false;
        } else if (mSampleFile != null && mSampleLength == 0) {

            return false;
        }
        return true;
    }

    public String getRecordDir() {
        return mSampleDir.getAbsolutePath();
    }

    public int getMaxAmplitude() {
        if (mState != RECORDING_STATE) {
            return 0;
        }
        return RecorderService.getMaxAmplitude();
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }

    public int state() {
        return mState;
    }

    public int progress() {
        if (mState == RECORDING_STATE) {
            return (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
        } else if (mState == PLAYING_STATE || mState == PLAYING_PAUSED_STATE) {
            if (mPlayer != null) {
                return (mPlayer.getCurrentPosition() / 1000);
            }
        }

        return 0;
    }

    public float playProgress() {
        if (mPlayer != null) {
            return ((float) mPlayer.getCurrentPosition()) / mPlayer.getDuration();
        }
        return 0.0f;
    }

    public int sampleLength() {
        return mSampleLength;
    }

    public File sampleFile() {
        return mSampleFile;
    }

    public void renameSampleFile(String name) {
        if (mSampleFile != null && mState != RECORDING_STATE && mState != PLAYING_STATE) {
            if (!TextUtils.isEmpty(name)) {
                String oldName = mSampleFile.getAbsolutePath();
                String extension = oldName.substring(oldName.lastIndexOf('.'));
                File newFile = new File(mSampleFile.getParent() + "/" + name + extension);
                if (!TextUtils.equals(oldName, newFile.getAbsolutePath())) {
                    if (mSampleFile.renameTo(newFile)) {
                        mSampleFile = newFile;
                    }
                }
            }
        }
    }

    public void delete() {
        stop();

        if (mSampleFile != null) {
            mSampleFile.delete();
        }

        mSampleFile = null;
        mSampleLength = 0;

        signalStateChanged(IDLE_STATE);
    }

    public void clear() {
        stop();
        mSampleLength = 0;
        signalStateChanged(IDLE_STATE);
    }

    public void reset() {
        stop();

        mSampleLength = 0;
        mSampleFile = null;
        mState = IDLE_STATE;

        File sampleDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + SAMPLE_DEFAULT_DIR);
        if (!sampleDir.exists()) {
            sampleDir.mkdirs();
        }
        mSampleDir = sampleDir;

        signalStateChanged(IDLE_STATE);
    }

    public boolean isRecordExisted(String path) {
        if (!TextUtils.isEmpty(path)) {
            File file = new File(mSampleDir.getAbsolutePath() + "/" + path);
            return file.exists();
        }
        return false;
    }

    public void startRecording(int outputFileFormat, String name, String extension,
                               boolean highQuality, long maxFileSize) {
        stop();

        if (mSampleFile == null) {
            try {
                mSampleFile = File.createTempFile(SAMPLE_PREFIX, extension, mSampleDir);
                renameSampleFile(name);
            } catch (IOException e) {
                setError(STORAGE_ACCESS_ERROR);
                return;
            }
        }

        RecorderService.startRecording(mContext, outputFileFormat, mSampleFile.getAbsolutePath(),
                highQuality, maxFileSize);
        mSampleStart = System.currentTimeMillis();
    }

    public void stopRecording() {
        if (RecorderService.isRecording()) {
            RecorderService.stopRecording(mContext);
            mSampleLength = (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
            if (mSampleLength == 0) {
                mSampleLength = 1;
            }
        }
    }

    public void startPlayback(float percentage) {
        if (state() == PLAYING_PAUSED_STATE) {
            mSampleStart = System.currentTimeMillis() - mPlayer.getCurrentPosition();
            mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
            mPlayer.start();
            setState(PLAYING_STATE);
        } else {
            stop();
            mPlayer = new MediaPlayer();
            try {
                Log.e(TAG, "startPlayback: " + mSampleFile.getAbsolutePath());
                mPlayer.setDataSource(mSampleFile.getAbsolutePath());
                mPlayer.setOnCompletionListener(this);
                mPlayer.setOnErrorListener(this);
                mPlayer.prepare();
                mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
                mPlayer.start();
            } catch (IllegalArgumentException e) {
                setError(INTERNAL_ERROR);
                mPlayer = null;
                return;
            } catch (IOException e) {
                setError(STORAGE_ACCESS_ERROR);
                mPlayer = null;
                return;
            }

            mSampleStart = System.currentTimeMillis();
            setState(PLAYING_STATE);
        }
    }

    public void pausePlayback() {
        if (mPlayer == null) {
            return;
        }

        mPlayer.pause();
        setState(PLAYING_PAUSED_STATE);
    }

    public void stopPlayback() {
        if (mPlayer == null) {
            return;
        }

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        setState(IDLE_STATE);
    }

    public void stop() {
        stopRecording();
        stopPlayback();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(STORAGE_ACCESS_ERROR);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stop();
    }

    public void setState(int state) {
        if (state == mState) {
            return;
        }

        mState = state;
        signalStateChanged(mState);
    }

    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null) {
            mOnStateChangedListener.onStateChanged(state);
        }
    }

    public void setError(int error) {
        if (mOnStateChangedListener != null) {
            mOnStateChangedListener.onError(error);
        }
    }
}
