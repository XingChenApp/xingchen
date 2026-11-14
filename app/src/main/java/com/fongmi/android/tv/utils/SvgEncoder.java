package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.EncodeStrategy;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.engine.Resource;
import com.caverock.androidsvg.SVG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 照官 8.83 {@code com.example.hikerview.lib.picture.SvgEncoder}（Kotlin）原文
 * 移植：SVG 渲染成 Picture → ARGB_8888 位图 → PNG(100) 落盘，供 Glide 磁盘
 * 缓存回写；策略 SOURCE。失败回 false 并留栈（官 printStackTrace 对位）。
 */
public final class SvgEncoder implements ResourceEncoder<SVG> {

    @NonNull
    @Override
    public EncodeStrategy getEncodeStrategy(@NonNull Options options) {
        return EncodeStrategy.SOURCE;
    }

    @Override
    public boolean encode(@NonNull Resource<SVG> data, @NonNull File file,
                          @NonNull Options options) {
        try {
            SVG svg = data.get();
            Picture picture = svg.renderToPicture();
            Bitmap bitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawPicture(picture);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.flush();
                try (OutputStream out = new FileOutputStream(file)) {
                    stream.writeTo(out);
                }
            } finally {
                bitmap.recycle();
                stream.close();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
