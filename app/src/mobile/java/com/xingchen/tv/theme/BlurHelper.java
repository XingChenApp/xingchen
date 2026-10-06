package com.xingchen.tv.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.io.File;
import java.io.FileOutputStream;

public class BlurHelper {
    
    public static Bitmap blur(Context ctx, Bitmap input) {
        if (input == null) return null;
        try {
            RenderScript rs = RenderScript.create(ctx);
            Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
            Allocation inAlloc = Allocation.createFromBitmap(rs, input);
            Allocation outAlloc = Allocation.createFromBitmap(rs, output);
            ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(25f);
            blur.setInput(inAlloc);
            blur.forEach(outAlloc);
            outAlloc.copyTo(output);
            rs.destroy();
            return output;
        } catch (Exception e) {
            return input;
        }
    }
    
    public static Bitmap loadAndBlur(Context ctx, String path) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null) return null;
            return blur(ctx, bmp);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static String saveBlurred(Context ctx, Bitmap blurred) {
        try {
            File outFile = new File(ctx.getCacheDir(), "wallpaper_blur.jpg");
            FileOutputStream fos = new FileOutputStream(outFile);
            blurred.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
