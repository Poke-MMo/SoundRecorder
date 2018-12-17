package com.xp.soundrecorder;

import android.content.DialogInterface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.chad.library.adapter.base.BaseQuickAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class RecordActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "RecordActivity";
    private Unbinder unbinder;
    @BindView(R.id.rv_record)
    RecyclerView rvRecord;
    private RecordAdapter mAdapter;

    private File mPath;

    private MediaPlayer mPlayer;
    private AlertDialog mDialog;
    private Button btnPlay;
    private Button btnPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        unbinder = ButterKnife.bind(this);

        initAdapter();
        // 判断SD卡是否存在，并且是否具有读写权限
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File sampleDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + Recorder.SAMPLE_DEFAULT_DIR);
            mAdapter.setNewData(getFileName(sampleDir.listFiles()));
        }
        
    }

    private void initAdapter() {
        LinearLayoutManager manager = new LinearLayoutManager(this);
        rvRecord.setLayoutManager(manager);
        rvRecord.addItemDecoration(new DividerDecoration(this));
        mAdapter = new RecordAdapter(R.layout.item_record);
        rvRecord.setAdapter(mAdapter);
        mAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                mPath = mAdapter.getData().get(position).getPath();
                initPlayer();
                showPlayDialog();
            }
        });

        mAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                mPath = mAdapter.getData().get(position).getPath();
                showDeleteConfirmDialog(position);
            }
        });
    }

    /**
     * 读取指定目录下的所有文件的文件名
     *
     * @param files 路径
     * @return 文件名数组
     */
    private List<RecordEntity> getFileName(File[] files) {
        List<RecordEntity> nameList = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    getFileName(file.listFiles());
                } else {
                    String fileName = file.getName();
                    if (fileName.endsWith(".amr")) {
                        nameList.add(new RecordEntity(file.getAbsoluteFile(), fileName));
                    }
                }
            }
        }
        return nameList;
    }

    private void showDeleteConfirmDialog(final int position) {
        AlertDialog.Builder mDialogBuilder = new AlertDialog.Builder(this);
        mDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.delete_dialog_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mPath != null) {
                            mPath.delete();
                            mAdapter.getData().remove(position);
                            mAdapter.notifyDataSetChanged();
                        }
                        dialog.dismiss();
                    }
                }).setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
    }

    private void showPlayDialog() {
        if (mDialog == null) {
            View view = LayoutInflater.from(this).inflate(R.layout.dlg_play_record, null, false);
            mDialog = new AlertDialog.Builder(this).setView(view).create();

            Button btnStop = view.findViewById(R.id.btn_stop);
            btnPlay = view.findViewById(R.id.btn_play);
            btnPause = view.findViewById(R.id.btn_pause);

            btnStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPlayer.reset(); //停止播放
                    btnPlay.setText(R.string.play);
                    btnPause.setText(R.string.pause);
                    initPlayer();

                }
            });

            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mPlayer.isPlaying()) {
                        mPlayer.start(); //开始播放
                        btnPlay.setText(R.string.playing);
                        btnPause.setText(R.string.pause);
                    }
                }
            });

            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mPlayer.isPlaying()) {
                        mPlayer.pause(); //暂停播放
                        btnPlay.setText(R.string.play);
                        btnPause.setText(R.string.pausing);
                    }
                }
            });
            mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    if (mPlayer.isPlaying()) {
                        mPlayer.reset();
                        btnPlay.setText(R.string.play);
                        btnPause.setText(R.string.pause);
                        initPlayer();
                    }
                }
            });
        }
        mDialog.show();
    }

    private void initPlayer() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mPath.getAbsolutePath());
            mPlayer.setOnCompletionListener(this);
            mPlayer.prepare();
        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
        }

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        btnPlay.setText(R.string.play);
        btnPause.setText(R.string.pause);
    }
}
