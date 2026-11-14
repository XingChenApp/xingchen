package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 照官 8.83 {@code com.example.hikerview.lib.picture.SvgDecoder} 原文移植：
 * InputStream → SVG（caverock androidsvg 解析），解析失败转 IOException 抛出。
 * handles 恒真——靠注册序兜底（在 OkGlideModule 里 append 在位图解码之后，
 * 位图能解的先走位图，解不出的 SVG 字节才落到这里）。
 */
public class SvgDecoder implements ResourceDecoder<InputStream, SVG> {

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) {
        return true;
    }

    @Nullable
    @Override
    public Resource<SVG> decode(@NonNull InputStream source, int width, int height,
                                @NonNull Options options) throws IOException {
        try {
            SVG svg = SVG.getFromInputStream(source);
            return new SimpleResource<>(svg);
        } catch (SVGParseException e) {
            throw new IOException("Cannot load SVG from stream", e);
        }
    }
}
