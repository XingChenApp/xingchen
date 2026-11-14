package com.fongmi.android.tv.utils;

import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.caverock.androidsvg.SVG;

/**
 * 照官 8.83 {@code com.example.hikerview.lib.picture.SvgDrawableTranscoder}
 * 原文移植：SVG → renderToPicture → PictureDrawable。
 */
public class SvgDrawableTranscoder implements ResourceTranscoder<SVG, PictureDrawable> {

    @Nullable
    @Override
    public Resource<PictureDrawable> transcode(@NonNull Resource<SVG> toTranscode,
                                               @NonNull Options options) {
        SVG svg = toTranscode.get();
        Picture picture = svg.renderToPicture();
        PictureDrawable drawable = new PictureDrawable(picture);
        return new SimpleResource<>(drawable);
    }
}
