package com.example.hikerview.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源）——本批只落与 GlideUtil 相连的
 * 压缩族六法（drawableToBitmap、compressImage 四重载、
 * calculateInSampleSize），逐字照官。ImgUtil 其余成员（存相册、
 * 截图、模糊等）耦合官浏览器/弹窗图加载面，随各自面再补，记名在案。
 */
public class ImgUtil {

    private static final String TAG = "ImgUtil";

    public interface OnSaveListener {
        void failed(String str);

        void success(List<String> list);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        Bitmap createBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, drawable.getOpacity() != -1 ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(createBitmap);
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
        drawable.draw(canvas);
        return createBitmap;
    }

    public static Bitmap compressImage(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int i = 100;
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        while (byteArrayOutputStream.toByteArray().length / 1024 > 300) {
            byteArrayOutputStream.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, i, byteArrayOutputStream);
            i -= 10;
        }
        return BitmapFactory.decodeStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()), null, null);
    }

    public static Bitmap compressImage(Bitmap bitmap, int i, int i2) {
        return Bitmap.createScaledBitmap(bitmap, i, i2, false);
    }

    public static Drawable compressImage(Resources resources, Drawable drawable, int i, int i2) {
        Bitmap bitmap;
        Bitmap createScaledBitmap;
        return (!(drawable instanceof BitmapDrawable) || (bitmap = ((BitmapDrawable) drawable).getBitmap()) == null || (createScaledBitmap = Bitmap.createScaledBitmap(bitmap, i, i2, false)) == bitmap) ? drawable : new BitmapDrawable(resources, createScaledBitmap);
    }

    public static byte[] compressImage(String str, int i, int i2, int i3) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(str, options);
        options.inSampleSize = calculateInSampleSize(options, i, i2);
        options.inJustDecodeBounds = false;
        Bitmap decodeFile = BitmapFactory.decodeFile(str, options);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        long j = i3 * 1024;
        int i4 = 100;
        while (true) {
            if ((i4 == 100 || byteArrayOutputStream.toByteArray().length > j) && i4 > 0) {
                byteArrayOutputStream.reset();
                i4 -= 5;
                Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;
                if (Build.VERSION.SDK_INT >= 30) {
                    compressFormat = Bitmap.CompressFormat.WEBP_LOSSY;
                }
                decodeFile.compress(compressFormat, i4, byteArrayOutputStream);
            } else {
                break;
            }
        }
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        if (!decodeFile.isRecycled()) {
            decodeFile.recycle();
        }
        return byteArray;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int i, int i2) {
        int i3 = options.outHeight;
        int i4 = options.outWidth;
        int i5 = 1;
        while (true) {
            if (i3 <= i2 && i4 <= i) {
                return i5;
            }
            i3 >>= 1;
            i4 >>= 1;
            i5 <<= 1;
        }
    }
}
