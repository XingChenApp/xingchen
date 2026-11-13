package jp.wasabeef.glide.transformations.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/**
 * 从 8.83 APK 内 wasabeef 库类逐行转写（smali_classes3 RSBlur.smali），保持原包名供适配器调用。
 */
public class RSBlur {

    public static Bitmap blur(Context context, Bitmap bitmap, int radius) throws RSRuntimeException {
        RenderScript rs = null;
        Allocation in = null;
        Allocation out = null;
        ScriptIntrinsicBlur blur = null;
        try {
            rs = RenderScript.create(context);
            rs.setMessageHandler(new RenderScript.RSMessageHandler());
            in = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, 1);
            out = Allocation.createTyped(rs, in.getType());
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setInput(in);
            blur.setRadius((float) radius);
            blur.forEach(out);
            out.copyTo(bitmap);
        } finally {
            if (rs != null) {
                RenderScript.releaseAllContexts();
            }
            if (in != null) {
                in.destroy();
            }
            if (out != null) {
                out.destroy();
            }
            if (blur != null) {
                blur.destroy();
            }
        }
        return bitmap;
    }
}
