package com.xp.soundrecorder;

import android.util.Log;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

public class RecordAdapter extends BaseQuickAdapter<RecordEntity, BaseViewHolder> {
    private static final String TAG = "RecordAdapter";

    public RecordAdapter(int layoutResId) {
        super(layoutResId);
    }

    @Override
    protected void convert(BaseViewHolder helper, RecordEntity item) {
        Log.e(TAG, "convert: " + item);
        helper.setText(R.id.tv_name, item.getName()).addOnClickListener(R.id.tv_del);
    }
}
