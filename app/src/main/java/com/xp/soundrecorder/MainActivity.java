package com.xp.soundrecorder;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

public class MainActivity extends AppCompatActivity implements Recorder.OnStateChangedListener {

    private static final String TAG = "MainActivity";
    private Unbinder unbinder;

    private static final String RECORDER_STATE_KEY = "recorder_state";

    private static final String SAMPLE_INTERRUPTED_KEY = "sample_interrupted";

    private static final String MAX_FILE_SIZE_KEY = "max_file_size";

    public static final String AUDIO_3GPP = "audio/3gpp";

    private static final String AUDIO_AMR = "audio/amr";

    private static final String AUDIO_ANY = "audio/*";

    private static final String FILE_EXTENSION_AMR = ".amr";

    private static final String FILE_EXTENSION_3GPP = ".3gpp";

    public static final int BITRATE_AMR = 2 * 1024 * 8; // bits/sec

    public static final int BITRATE_3GPP = 20 * 1024 * 8; // bits/sec

    private static final int SEEK_BAR_MAX = 10000;

    private String mRequestedType = AUDIO_3GPP;

    private boolean mCanRequestChanged = false;

    private Recorder mRecorder;

    private RecorderReceiver mReceiver;

    private boolean mSampleInterrupted = false;

    private boolean mShowFinishButton = false;

    private String mErrorUiMessage = null; // Some error messages are displayed
    // in the UI, not a dialog. This
    // happens when a recording
    // is interrupted for some reason.

    private long mMaxFileSize = -1; // can be specified in the intent

    private RemainingTimeCalculator mRemainingTimeCalculator;

    private String mTimerFormat;

//    private SoundPool mSoundPool;

//    private int mPlaySound;

//    private int mPauseSound;

    private HashSet<String> mSavedRecord;

    private long mLastClickTime;

    private int mLastButtonId;

    private final Handler mHandler = new Handler();

    private Runnable mUpdateTimer = new Runnable() {
        public void run() {
            if (!mStopUiUpdate) {
                updateTimerView();
            }
        }
    };

    private Runnable mUpdateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (!mStopUiUpdate) {
                updateSeekBar();
            }
        }
    };

    private Runnable mUpdateVUMeter = new Runnable() {
        @Override
        public void run() {
            if (!mStopUiUpdate) {
                updateVUMeterView();
            }
        }
    };
    @BindView(R.id.newButton)
    ImageButton mNewButton;
    @BindView(R.id.finishButton)
    ImageButton mFinishButton;
    @BindView(R.id.recordButton)
    ImageButton mRecordButton;
    @BindView(R.id.stopButton)
    ImageButton mStopButton;
    @BindView(R.id.playButton)
    ImageButton mPlayButton;
    @BindView(R.id.pauseButton)
    ImageButton mPauseButton;
    @BindView(R.id.deleteButton)
    ImageButton mDeleteButton;

    @BindView(R.id.file_name)
    RecordNameEditText mFileNameEditText;
    @BindView(R.id.time_calculator)
    LinearLayout mTimerLayout;
    @BindView(R.id.vumeter_layout)
    LinearLayout mVUMeterLayout;
    @BindView(R.id.play_seek_bar_layout)
    LinearLayout mSeekBarLayout;
    @BindView(R.id.starttime)
    TextView mStartTime;
    @BindView(R.id.totaltime)
    TextView mTotalTime;
    @BindView(R.id.play_seek_bar)
    SeekBar mPlaySeekBar;

    private BroadcastReceiver mSDCardMountEventReceiver = null;

    private int mPreviousVUMax;

    private boolean mStopUiUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);
        mRecorder = new Recorder(this);
        mRecorder.setOnStateChangedListener(this);
        mReceiver = new RecorderReceiver();
        mRemainingTimeCalculator = new RemainingTimeCalculator();
        mSavedRecord = new HashSet<>();

        initResourceRefs();

        setResult(RESULT_CANCELED);
        registerExternalStorageListener();
        if (savedInstanceState != null) {
            Bundle recorderState = savedInstanceState.getBundle(RECORDER_STATE_KEY);
            if (recorderState != null) {
                mRecorder.restoreState(recorderState);
                mSampleInterrupted = recorderState.getBoolean(SAMPLE_INTERRUPTED_KEY, false);
                mMaxFileSize = recorderState.getLong(MAX_FILE_SIZE_KEY, -1);
            }
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (mShowFinishButton) {
            // reset state if it is a recording request
            mRecorder.reset();
            resetFileNameEditText();
        }

    }

    private void initResourceRefs() {

//        mNewButton.setOnClickListener(this);
//        mFinishButton.setOnClickListener(this);
//        mRecordButton.setOnClickListener(this);
//        mStopButton.setOnClickListener(this);
//        mPlayButton.setOnClickListener(this);
//        mPauseButton.setOnClickListener(this);
//        mDeleteButton.setOnClickListener(this);


        resetFileNameEditText();
        mFileNameEditText.setNameChangeListener(new RecordNameEditText.OnNameChangeListener() {
            @Override
            public void onNameChanged(String name) {
                if (!TextUtils.isEmpty(name)) {
                    mRecorder.renameSampleFile(name);
                }
            }
        });


        mPlaySeekBar.setMax(SEEK_BAR_MAX);
        mPlaySeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);

        mTimerFormat = getResources().getString(R.string.timer_format);

        if (mShowFinishButton) {
            mNewButton.setVisibility(View.GONE);
            mFinishButton.setVisibility(View.VISIBLE);
            mNewButton = mFinishButton; // use mNewButon variable for left
            // button in the control panel
        }

//        mSoundPool = new SoundPool(5, AudioManager.STREAM_SYSTEM, 5);
//        mPlaySound = mSoundPool.load("/system/media/audio/ui/SoundRecorderPlay.ogg", 1);
//        mPauseSound = mSoundPool.load("/system/media/audio/ui/SoundRecorderPause.ogg", 1);

        mLastClickTime = 0;
        mLastButtonId = 0;
    }

    private void resetFileNameEditText() {
        String extension = "";
        if (AUDIO_AMR.equals(mRequestedType)) {
            extension = FILE_EXTENSION_AMR;
        } else if (AUDIO_3GPP.equals(mRequestedType)) {
            extension = FILE_EXTENSION_3GPP;
        }

        // for audio which is used for mms, we can only use english file name
        // mShowFinishButon indicates whether this is an audio for mms
        mFileNameEditText.initFileName(mRecorder.getRecordDir(), extension, mShowFinishButton);
    }

    @OnClick({R.id.newButton, R.id.recordButton, R.id.stopButton,
            R.id.playButton, R.id.pauseButton, R.id.finishButton,
            R.id.deleteButton})
    public void onClick(View v) {
        if (System.currentTimeMillis() - mLastClickTime < 300) {
            // in order to avoid user click bottom too quickly
            return;
        }

        if (!v.isEnabled())
            return;

        if (v.getId() == mLastButtonId && v.getId() != R.id.newButton) {
            // as the recorder state is async with the UI
            // we need to avoid launching the duplicated action
            return;
        }

        if (v.getId() == R.id.stopButton && System.currentTimeMillis() - mLastClickTime < 1500) {
            // it seems that the media recorder is not robust enough
            // sometime it crashes when stop recording right after starting
            return;
        }

        mLastClickTime = System.currentTimeMillis();
        mLastButtonId = v.getId();

        switch (v.getId()) {
            case R.id.newButton:
                mFileNameEditText.clearFocus();
                saveSample();
                mRecorder.reset();
                resetFileNameEditText();
                break;
            case R.id.recordButton:
                showOverwriteConfirmDialogIfConflicts();
                break;
            case R.id.stopButton:
                mRecorder.stop();
                break;
            case R.id.playButton:
                mRecorder.startPlayback(mRecorder.playProgress());
                break;
            case R.id.pauseButton:
                mRecorder.pausePlayback();
                break;
            case R.id.finishButton:
                mRecorder.stop();
                saveSample();
                finish();
                break;
            case R.id.deleteButton:
                showDeleteConfirmDialog();
                break;
        }
    }

    @OnClick(R.id.btn_record)
    public void clickToRecord() {
        startActivity(new Intent(MainActivity.this, RecordActivity.class));
    }

    private void stopAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");

        sendBroadcast(i);
    }

    @Override
    public void onStateChanged(int state) {
        if (state == Recorder.PLAYING_STATE || state == Recorder.RECORDING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
        }

        updateUi(false);
    }

    @Override
    public void onError(int error) {
        Resources res = getResources();

        String message = null;
        switch (error) {
            case Recorder.STORAGE_ACCESS_ERROR:
                message = res.getString(R.string.error_sdcard_access);
                break;
            case Recorder.IN_CALL_RECORD_ERROR:
                // TODO: update error message to reflect that the recording
                // could not be
                // performed during a call.
            case Recorder.INTERNAL_ERROR:
                message = res.getString(R.string.error_app_internal);
                break;
        }
        if (message != null) {
            new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(message)
                    .setPositiveButton(R.string.button_ok, null).setCancelable(false).show();
        }
    }

    private void startRecording() {
        mRemainingTimeCalculator.reset();
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mSampleInterrupted = true;
            mErrorUiMessage = getResources().getString(R.string.insert_sd_card);
            updateUi(false);
        } else if (!mRemainingTimeCalculator.diskSpaceAvailable()) {
            mSampleInterrupted = true;
            mErrorUiMessage = getResources().getString(R.string.storage_is_full);
            updateUi(false);
        } else {
            stopAudioPlayback();

            boolean isHighQuality = SoundRecorderPreferenceActivity.isHighQuality(this);
            if (AUDIO_AMR.equals(mRequestedType)) {
                mRemainingTimeCalculator.setBitRate(BITRATE_AMR);
                int outputfileformat = isHighQuality ? MediaRecorder.OutputFormat.AMR_WB
                        : MediaRecorder.OutputFormat.AMR_NB;
                mRecorder.startRecording(outputfileformat, mFileNameEditText.getText().toString(),
                        FILE_EXTENSION_AMR, isHighQuality, mMaxFileSize);
            } else if (AUDIO_3GPP.equals(mRequestedType)) {
                // HACKME: for HD2, there is an issue with high quality 3gpp
                // use low quality instead
                if (Build.MODEL.equals("HTC HD2")) {
                    isHighQuality = false;
                }

                mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
                mRecorder.startRecording(MediaRecorder.OutputFormat.THREE_GPP, mFileNameEditText
                        .getText().toString(), FILE_EXTENSION_3GPP, isHighQuality, mMaxFileSize);
            } else {
                throw new IllegalArgumentException("Invalid output file type requested");
            }

            if (mMaxFileSize != -1) {
                mRemainingTimeCalculator.setFileSizeLimit(mRecorder.sampleFile(), mMaxFileSize);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (mRecorder.state()) {
                case Recorder.IDLE_STATE:
                case Recorder.PLAYING_PAUSED_STATE:
                    if (mRecorder.sampleLength() > 0)
                        saveSample();
                    finish();
                    break;
                case Recorder.PLAYING_STATE:
                    mRecorder.stop();
                    saveSample();
                    break;
                case Recorder.RECORDING_STATE:
                    if (mShowFinishButton) {
                        mRecorder.clear();
                    } else {
                        finish();
                    }
                    break;
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String type = SoundRecorderPreferenceActivity.getRecordType(this);
        if (mCanRequestChanged && !TextUtils.equals(type, mRequestedType)) {
            saveSample();
            mRecorder.reset();
            mRequestedType = type;
            resetFileNameEditText();
        }
        mCanRequestChanged = false;

        if (!mRecorder.syncStateWithService()) {
            mRecorder.reset();
            resetFileNameEditText();
        }

        if (mRecorder.state() == Recorder.RECORDING_STATE) {
            String preExtension = AUDIO_AMR.equals(mRequestedType) ? FILE_EXTENSION_AMR
                    : FILE_EXTENSION_3GPP;
            if (!mRecorder.sampleFile().getName().endsWith(preExtension)) {
                // the extension is changed need to stop current recording
                mRecorder.reset();
                resetFileNameEditText();
            } else {
                // restore state
                if (!mShowFinishButton) {
                    String fileName = mRecorder.sampleFile().getName().replace(preExtension, "");
                    mFileNameEditText.setText(fileName);
                }

                if (AUDIO_AMR.equals(mRequestedType)) {
                    mRemainingTimeCalculator.setBitRate(BITRATE_AMR);
                } else if (AUDIO_3GPP.equals(mRequestedType)) {
                    mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
                }
            }
        } else {
            File file = mRecorder.sampleFile();
            if (file != null && !file.exists()) {
                mRecorder.reset();
                resetFileNameEditText();
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(RecorderService.RECORDER_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mStopUiUpdate = false;
        updateUi(true);

        if (RecorderService.isRecording()) {
            Intent intent = new Intent(this, RecorderService.class);
            intent.putExtra(RecorderService.ACTION_NAME,
                    RecorderService.ACTION_DISABLE_MONITOR_REMAIN_TIME);
            startService(intent);
        }
    }

    @Override
    protected void onPause() {
        if (mRecorder.state() != Recorder.RECORDING_STATE || mShowFinishButton
                || mMaxFileSize != -1) {
            mRecorder.stop();
            saveSample();
            mFileNameEditText.clearFocus();
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .cancel(RecorderService.NOTIFICATION_ID);
        }

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }

        mCanRequestChanged = true;
        mStopUiUpdate = true;
//        stopAnimation();

        if (RecorderService.isRecording()) {
            Intent intent = new Intent(this, RecorderService.class);
            intent.putExtra(RecorderService.ACTION_NAME,
                    RecorderService.ACTION_ENABLE_MONITOR_REMAIN_TIME);
            startService(intent);
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mShowFinishButton) {
            finish();
        }
        super.onStop();
    }

    /*
     * If we have just recorded a sample, this adds it to the media data base
     * and sets the result to the sample's URI.
     */
    private void saveSample() {
        if (mRecorder.sampleLength() == 0)
            return;
        if (!mSavedRecord.contains(mRecorder.sampleFile().getAbsolutePath())) {
            Uri uri = null;
            try {
                uri = this.addToMediaDB(mRecorder.sampleFile());
            } catch (UnsupportedOperationException ex) { // Database
                // manipulation
                // failure
                return;
            }
            if (uri == null) {
                return;
            }
            mSavedRecord.add(mRecorder.sampleFile().getAbsolutePath());
            setResult(RESULT_OK, new Intent().setData(uri));
        }
    }

    private void showDeleteConfirmDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        dialogBuilder.setTitle(R.string.delete_dialog_title);
        dialogBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mRecorder.delete();
            }
        });
        dialogBuilder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLastButtonId = 0;
                    }
                });
        dialogBuilder.show();
    }

    private void showOverwriteConfirmDialogIfConflicts() {
        String fileName = mFileNameEditText.getText().toString()
                + (AUDIO_AMR.equals(mRequestedType) ? FILE_EXTENSION_AMR : FILE_EXTENSION_3GPP);

        if (mRecorder.isRecordExisted(fileName) && !mShowFinishButton) {
            // file already existed and it's not a recording request from other
            // app
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
            dialogBuilder.setTitle(getString(R.string.overwrite_dialog_title, fileName));
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startRecording();
                        }
                    });
            dialogBuilder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mLastButtonId = 0;
                        }
                    });
            dialogBuilder.show();
        } else {
            startRecording();
        }
    }

    /*
     * Called on destroy to unregister the SD card mount event receiver.
     */
    @Override
    public void onDestroy() {
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
//        mSoundPool.release();
        unbinder.unbind();
        super.onDestroy();
    }

    /*
     * Registers an intent to listen for
     * ACTION_MEDIA_EJECT/ACTION_MEDIA_UNMOUNTED/ACTION_MEDIA_MOUNTED
     * notifications.
     */
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mSampleInterrupted = false;
                    mRecorder.reset();
                    resetFileNameEditText();
                    updateUi(false);
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    /*
     * A simple utility to do a query into the databases.
     */
    private Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                         String sortOrder) {
        try {
            ContentResolver resolver = getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    /*
     * Add the given audioId to the playlist with the given playlistId; and
     * maintain the play_order in the playlist.
     */
    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        resolver.insert(uri, values);
    }

    /*
     * Obtain the id for the default play list from the audio_playlists table.
     */
    private int getPlaylistId(Resources res) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{
                MediaStore.Audio.Playlists._ID
        };
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{
                res.getString(R.string.audio_db_playlist_name)
        };
        Cursor cursor = query(uri, ids, where, args, null);
        if (cursor == null) {
            Log.v(TAG, "query returns null");
        }
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        }
        return id;
    }

    /*
     * Create a playlist with the given default playlist name, if no such
     * playlist exists.
     */
    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, res.getString(R.string.audio_db_playlist_name));
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            new AlertDialog.Builder(this).setTitle(R.string.app_name)
                    .setMessage(R.string.error_mediadb_new_record)
                    .setPositiveButton(R.string.button_ok, null).setCancelable(false).show();
        }
        return uri;
    }

    /*
     * Adds file and returns content uri.
     */
    private Uri addToMediaDB(File file) {
        Resources res = getResources();
        ContentValues cv = new ContentValues();
        long current = System.currentTimeMillis();
        long modDate = file.lastModified();
        Date date = new Date(current);
        SimpleDateFormat formatter = new SimpleDateFormat(
                res.getString(R.string.audio_db_title_format));
        String title = formatter.format(date);
        long sampleLengthMillis = mRecorder.sampleLength() * 1000L;

        // Lets label the recorded audio file as NON-MUSIC so that the file
        // won't be displayed automatically, except for in the playlist.
        cv.put(MediaStore.Audio.Media.IS_MUSIC, "0");

        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, sampleLengthMillis);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mRequestedType);
        cv.put(MediaStore.Audio.Media.ARTIST, res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM, res.getString(R.string.audio_db_album_name));
        Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "ContentURI: " + base);
        Uri result = resolver.insert(base, cv);
        if (result == null) {
            Log.w(TAG, getString(R.string.error_mediadb_new_record));
            return null;
        }

        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));

        // Notify those applications such as Music listening to the
        // scanner events that a recorded audio file just created.
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    private ImageView getTimerImage(char number) {
        ImageView image = new ImageView(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (number != ':') {
            image.setBackgroundResource(R.mipmap.background_number);
        }
        switch (number) {
            case '0':
                image.setImageResource(R.mipmap.number_0);
                break;
            case '1':
                image.setImageResource(R.mipmap.number_1);
                break;
            case '2':
                image.setImageResource(R.mipmap.number_2);
                break;
            case '3':
                image.setImageResource(R.mipmap.number_3);
                break;
            case '4':
                image.setImageResource(R.mipmap.number_4);
                break;
            case '5':
                image.setImageResource(R.mipmap.number_5);
                break;
            case '6':
                image.setImageResource(R.mipmap.number_6);
                break;
            case '7':
                image.setImageResource(R.mipmap.number_7);
                break;
            case '8':
                image.setImageResource(R.mipmap.number_8);
                break;
            case '9':
                image.setImageResource(R.mipmap.number_9);
                break;
            case ':':
                image.setImageResource(R.mipmap.colon);
                break;
        }
        image.setLayoutParams(lp);
        return image;
    }

    /**
     * Update the big MM:SS timer. If we are in playback, also update the
     * progress bar.
     */
    private void updateTimerView() {
        int state = mRecorder.state();

        boolean ongoing = state == Recorder.RECORDING_STATE || state == Recorder.PLAYING_STATE;

        long time = mRecorder.progress();
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mTimerLayout.removeAllViews();
        for (int i = 0; i < timeStr.length(); i++) {
            mTimerLayout.addView(getTimerImage(timeStr.charAt(i)));
        }

        if (state == Recorder.RECORDING_STATE) {
            updateTimeRemaining();
        }

        if (ongoing) {
            mHandler.postDelayed(mUpdateTimer, 500);
        }
    }

    private void setTimerView(float progress) {
        long time = (long) (progress * mRecorder.sampleLength());
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mTimerLayout.removeAllViews();
        for (int i = 0; i < timeStr.length(); i++) {
            mTimerLayout.addView(getTimerImage(timeStr.charAt(i)));
        }
    }

    private void updateSeekBar() {
        if (mRecorder.state() == Recorder.PLAYING_STATE) {
            mPlaySeekBar.setProgress((int) (SEEK_BAR_MAX * mRecorder.playProgress()));
            mHandler.postDelayed(mUpdateSeekBar, 10);
        }
    }

    /*
     * Called when we're in recording state. Find out how much longer we can go
     * on recording. If it's under 5 minutes, we display a count-down in the UI.
     * If we've run out of time, stop the recording.
     */
    private void updateTimeRemaining() {
        long t = mRemainingTimeCalculator.timeRemaining();

        if (t <= 0) {
            mSampleInterrupted = true;

            int limit = mRemainingTimeCalculator.currentLowerLimit();
            switch (limit) {
                case RemainingTimeCalculator.DISK_SPACE_LIMIT:
                    mErrorUiMessage = getResources().getString(R.string.storage_is_full);
                    break;
                case RemainingTimeCalculator.FILE_SIZE_LIMIT:
                    mErrorUiMessage = getResources().getString(R.string.max_length_reached);
                    break;
                default:
                    mErrorUiMessage = null;
                    break;
            }

            mRecorder.stop();
        }
    }

    private void updateVUMeterView() {
        final int MAX_VU_SIZE = 11;
        boolean showVUArray[] = new boolean[MAX_VU_SIZE];

        if (mVUMeterLayout.getVisibility() == View.VISIBLE
                && mRecorder.state() == Recorder.RECORDING_STATE) {
            int vuSize = MAX_VU_SIZE * mRecorder.getMaxAmplitude() / 32768;
            if (vuSize >= MAX_VU_SIZE) {
                vuSize = MAX_VU_SIZE - 1;
            }

            if (vuSize >= mPreviousVUMax) {
                mPreviousVUMax = vuSize;
            } else if (mPreviousVUMax > 0) {
                mPreviousVUMax--;
            }

            for (int i = 0; i < MAX_VU_SIZE; i++) {
                if (i <= vuSize) {
                    showVUArray[i] = true;
                } else if (i == mPreviousVUMax) {
                    showVUArray[i] = true;
                } else {
                    showVUArray[i] = false;
                }
            }

            mHandler.postDelayed(mUpdateVUMeter, 100);
        } else if (mVUMeterLayout.getVisibility() == View.VISIBLE) {
            mPreviousVUMax = 0;
            for (int i = 0; i < MAX_VU_SIZE; i++) {
                showVUArray[i] = false;
            }
        }

        if (mVUMeterLayout.getVisibility() == View.VISIBLE) {
            mVUMeterLayout.removeAllViews();
            for (boolean show : showVUArray) {
                ImageView imageView = new ImageView(this);
                imageView.setBackgroundResource(R.mipmap.background_vumeter);
                if (show) {
                    imageView.setImageResource(R.mipmap.icon_vumeter);
                }
                imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                mVUMeterLayout.addView(imageView);
            }
        }
    }

    /**
     * Shows/hides the appropriate child views for the new state.
     */
    private void updateUi(boolean skipRewindAnimation) {
        switch (mRecorder.state()) {
            case Recorder.IDLE_STATE:
                mLastButtonId = 0;
            case Recorder.PLAYING_PAUSED_STATE:
                if (mRecorder.sampleLength() == 0) {
                    mNewButton.setEnabled(true);
                    mNewButton.setVisibility(View.VISIBLE);
                    mRecordButton.setVisibility(View.VISIBLE);
                    mStopButton.setVisibility(View.GONE);
                    mPlayButton.setVisibility(View.GONE);
                    mPauseButton.setVisibility(View.GONE);
                    mDeleteButton.setEnabled(false);
                    mRecordButton.requestFocus();

                    mVUMeterLayout.setVisibility(View.VISIBLE);
                    mSeekBarLayout.setVisibility(View.GONE);
                } else {
                    mNewButton.setEnabled(true);
                    mNewButton.setVisibility(View.VISIBLE);
                    mRecordButton.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.GONE);
                    mPlayButton.setVisibility(View.VISIBLE);
                    mPauseButton.setVisibility(View.GONE);
                    mDeleteButton.setEnabled(true);
                    mPauseButton.requestFocus();

                    mVUMeterLayout.setVisibility(View.GONE);
                    mSeekBarLayout.setVisibility(View.VISIBLE);
                    mStartTime.setText(String.format(mTimerFormat, 0, 0));
                    mTotalTime.setText(String.format(mTimerFormat, mRecorder.sampleLength() / 60,
                            mRecorder.sampleLength() % 60));
                }
                mFileNameEditText.setEnabled(true);
                mFileNameEditText.clearFocus();

                if (mRecorder.sampleLength() > 0) {
                    if (mRecorder.state() == Recorder.PLAYING_PAUSED_STATE) {
//                        stopAnimation();
//                        if (SoundRecorderPreferenceActivity.isEnabledSoundEffect(this)) {
//                            mSoundPool.play(mPauseSound, 1.0f, 1.0f, 0, 0, 1);
//                        }
                    } else {
                        mPlaySeekBar.setProgress(0);
                        if (!skipRewindAnimation) {
//                            stopRecordPlayingAnimation();
                        } else {
//                            stopAnimation();
                        }
                    }
                } else {
//                    stopAnimation();
                }

                // we allow only one toast at one time
                if (mSampleInterrupted && mErrorUiMessage == null) {
                    Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show();
                }

                if (mErrorUiMessage != null) {
                    Toast.makeText(this, mErrorUiMessage, Toast.LENGTH_SHORT).show();
                }

                break;
            case Recorder.RECORDING_STATE:
                mNewButton.setEnabled(false);
                mNewButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
                mPlayButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.GONE);
                mDeleteButton.setEnabled(false);
                mStopButton.requestFocus();

                mVUMeterLayout.setVisibility(View.VISIBLE);
                mSeekBarLayout.setVisibility(View.GONE);

                mFileNameEditText.setEnabled(false);

//                startRecordPlayingAnimation();
                mPreviousVUMax = 0;
                break;

            case Recorder.PLAYING_STATE:
                mNewButton.setEnabled(false);
                mNewButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.GONE);
                mPlayButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.VISIBLE);
                mDeleteButton.setEnabled(false);
                mPauseButton.requestFocus();

                mVUMeterLayout.setVisibility(View.GONE);
                mSeekBarLayout.setVisibility(View.VISIBLE);

                mFileNameEditText.setEnabled(false);

//                if (SoundRecorderPreferenceActivity.isEnabledSoundEffect(this)) {
//                    mSoundPool.play(mPlaySound, 1.0f, 1.0f, 0, 0, 1);
//                }
//                startRecordPlayingAnimation();
                break;
        }

        updateTimerView();
        updateSeekBar();
        updateVUMeterView();

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mRecorder.state() == Recorder.RECORDING_STATE
                || mRecorder.state() == Recorder.PLAYING_STATE) {
            return false;
        } else {
//            getMenuInflater().inflate(R.layout.view_list_menu, menu);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        /*switch (item.getItemId()) {
            case R.id.menu_fm:
                saveSample();
                intent = new Intent();
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("file://" + mRecorder.getRecordDir()));
                startActivity(intent);
                break;
            case R.id.menu_setting:
                intent = new Intent(this, SoundRecorderPreferenceActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }*/
        return true;
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        private final int DELTA = SEEK_BAR_MAX / 20;

        private int mProgress = 0;

        private boolean mPlayingAnimation = false;

        private boolean mForwardAnimation = true;

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
//            stopAnimation();
            mRecorder.startPlayback((float) seekBar.getProgress() / SEEK_BAR_MAX);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mRecorder.pausePlayback();
            mPlayingAnimation = false;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                if (!mPlayingAnimation) {
                    mForwardAnimation = true;
//                    startForwardAnimation();
                    mPlayingAnimation = true;
                    mProgress = progress;
                }

                if (progress >= mProgress + DELTA) {
                    if (!mForwardAnimation) {
                        mForwardAnimation = true;
//                        stopAnimation();
//                        startForwardAnimation();
                    }
                    mProgress = progress;
                } else if (progress < mProgress - DELTA) {
                    if (mForwardAnimation) {
                        mForwardAnimation = false;
//                        stopAnimation();
//                        startBackwardAnimation();
                    }
                    mProgress = progress;
                }

                setTimerView(((float) progress) / SEEK_BAR_MAX);
                mLastButtonId = 0;
            }
        }
    };

    private class RecorderReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(RecorderService.RECORDER_SERVICE_BROADCAST_STATE)) {
                boolean isRecording = intent.getBooleanExtra(
                        RecorderService.RECORDER_SERVICE_BROADCAST_STATE, false);
                mRecorder.setState(isRecording ? Recorder.RECORDING_STATE : Recorder.IDLE_STATE);
            } else if (intent.hasExtra(RecorderService.RECORDER_SERVICE_BROADCAST_ERROR)) {
                int error = intent.getIntExtra(RecorderService.RECORDER_SERVICE_BROADCAST_ERROR, 0);
                mRecorder.setError(error);
            }
        }
    }
}

