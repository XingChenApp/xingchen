package com.example.hikerview.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import com.annimon.stream.function.Consumer;
import com.bumptech.glide.Glide;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.view.PopImageLoaderNoView;
import com.example.hikerview.ui.view.popup.XPopupImageLoader;
import com.lxj.xpopup.util.XPermission;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源）——先落与 GlideUtil 相连的
 * 压缩族六法（drawableToBitmap、compressImage 四重载、
 * calculateInSampleSize），逐字照官。
 * 2026-11-14 对官补缺再落：dip2px、px2dip、getStatusBarHeight、
 * doBlur 三重载、downloadImgByGlide（+私有 downloadImgByGlideSync）。
 * 存相册族已落（2026-11-14 图片团，潇洒哥亲裁引 subscaleview 库）：
 * savePic2Gallery 三重载、saveBmpToAlbum 及私有 writeFileFromIS、
 * getFileExt 照官行 486–676 转写；签名类型 XPopupImageLoader 与查看器面
 * MyImageViewerPopupView、PopImageLoaderNoView 同批照官落树，
 * ShareUtil.getMimeTypeByExt 同批补。官体细节与 c2 账一处出入已按官
 * smali 订正：SDK 29+ 支的流拷贝是 android.os.FileUtils.copy（框架类），
 * 不是 commons-io FileUtils（见 hk-img-fill-20261114.md）。
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

    // ---- 2026-11-14 对官补缺（官 smali_classes2 ImgUtil.smali） ----

    /** dip 转 px（官行 288–311）：(int)(dip × density + 0.5f)。 */
    public static int dip2px(Context context, float f) {
        return (int) (f * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** px 转 dip（官行 2102–2125）：(int)(px ÷ density + 0.5f)。 */
    public static int px2dip(Context context, float f) {
        return (int) (f / context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 状态栏高度（官行 1469–1528）：反射 com.android.internal.R$dimen 的
     * status_bar_height 资源 id 再取像素值；任何异常 printStackTrace 后
     * 返回 0（官原样，非兜底改写）。
     */
    public static int getStatusBarHeight(Context context) {
        int i = 0;
        try {
            Class<?> cls = Class.forName("com.android.internal.R$dimen");
            Object obj = cls.newInstance();
            int id = Integer.parseInt(cls.getField("status_bar_height").get(obj).toString());
            i = context.getResources().getDimensionPixelSize(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return i;
    }

    /** 先按 i 等比缩小再模糊（官行 313–346）：缩放图模糊后回收缩放图。 */
    public static Bitmap doBlur(Bitmap bitmap, int i, int i2) {
        Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / i, bitmap.getHeight() / i, false);
        Bitmap doBlur = doBlur(createScaledBitmap, i2, false);
        createScaledBitmap.recycle();
        return doBlur;
    }

    /** 先抽缩略图再模糊（官行 348–367）：缩略图模糊后回收缩略图。 */
    public static Bitmap doBlur(Bitmap bitmap, int i, int i2, int i3) {
        Bitmap extractThumbnail = ThumbnailUtils.extractThumbnail(bitmap, i, i2);
        Bitmap doBlur = doBlur(extractThumbnail, i3, true);
        extractThumbnail.recycle();
        return doBlur;
    }

    /**
     * Stack Blur 本体（官行 369–1245，逐项对 smali 核过：mul_table 以
     * div_sum=((2r+1)+1)>>1 的平方为除数、行列两趟滑动窗口、栈
     * int[div][3]、末尾 setPixels 回写）：z 为真直接在入图上改，
     * 否则先 copy 一份可变副本；半径小于 1 返回 null（官原样）。
     */
    public static Bitmap doBlur(Bitmap bitmap, int i, boolean z) {
        Bitmap bitmap2;
        if (z) {
            bitmap2 = bitmap;
        } else {
            bitmap2 = bitmap.copy(bitmap.getConfig(), true);
        }
        if (i < 1) {
            return null;
        }
        int w = bitmap2.getWidth();
        int h = bitmap2.getHeight();
        int[] pix = new int[w * h];
        bitmap2.getPixels(pix, 0, w, 0, 0, w, h);
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = i + i + 1;
        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rSum, gSum, bSum, x, y, idx, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];
        int divSum = (div + 1) >> 1;
        divSum *= divSum;
        int[] mulTable = new int[256 * divSum];
        for (int j = 0; j < 256 * divSum; j++) {
            mulTable[j] = j / divSum;
        }
        int[][] stack = new int[div][3];
        int stackPointer;
        int[] sir;
        int rbs;
        int r1 = i + 1;
        int routSum, goutSum, boutSum, rinSum, ginSum, binSum;
        yw = yi = 0;
        for (y = 0; y < h; y++) {
            binSum = ginSum = rinSum = boutSum = goutSum = routSum = bSum = gSum = rSum = 0;
            for (idx = -i; idx <= i; idx++) {
                p = pix[yi + Math.min(wm, Math.max(idx, 0))];
                sir = stack[idx + i];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                rbs = r1 - Math.abs(idx);
                rSum += sir[0] * rbs;
                gSum += sir[1] * rbs;
                bSum += sir[2] * rbs;
                if (idx > 0) {
                    rinSum += sir[0];
                    ginSum += sir[1];
                    binSum += sir[2];
                } else {
                    routSum += sir[0];
                    goutSum += sir[1];
                    boutSum += sir[2];
                }
            }
            stackPointer = i;
            for (x = 0; x < w; x++) {
                r[yi] = mulTable[rSum];
                g[yi] = mulTable[gSum];
                b[yi] = mulTable[bSum];
                rSum -= routSum;
                gSum -= goutSum;
                bSum -= boutSum;
                sir = stack[(stackPointer - i + div) % div];
                routSum -= sir[0];
                goutSum -= sir[1];
                boutSum -= sir[2];
                if (y == 0) {
                    vmin[x] = Math.min(x + i + 1, wm);
                }
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                rinSum += sir[0];
                ginSum += sir[1];
                binSum += sir[2];
                rSum += rinSum;
                gSum += ginSum;
                bSum += binSum;
                stackPointer = (stackPointer + 1) % div;
                sir = stack[stackPointer];
                routSum += sir[0];
                goutSum += sir[1];
                boutSum += sir[2];
                rinSum -= sir[0];
                ginSum -= sir[1];
                binSum -= sir[2];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            binSum = ginSum = rinSum = boutSum = goutSum = routSum = bSum = gSum = rSum = 0;
            yp = -i * w;
            for (idx = -i; idx <= i; idx++) {
                yi = Math.max(0, yp) + x;
                sir = stack[idx + i];
                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];
                rbs = r1 - Math.abs(idx);
                rSum += r[yi] * rbs;
                gSum += g[yi] * rbs;
                bSum += b[yi] * rbs;
                if (idx > 0) {
                    rinSum += sir[0];
                    ginSum += sir[1];
                    binSum += sir[2];
                } else {
                    routSum += sir[0];
                    goutSum += sir[1];
                    boutSum += sir[2];
                }
                if (idx < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackPointer = i;
            for (y = 0; y < h; y++) {
                pix[yi] = (pix[yi] & 0xff000000) | (mulTable[rSum] << 16) | (mulTable[gSum] << 8) | mulTable[bSum];
                rSum -= routSum;
                gSum -= goutSum;
                bSum -= boutSum;
                sir = stack[(stackPointer - i + div) % div];
                routSum -= sir[0];
                goutSum -= sir[1];
                boutSum -= sir[2];
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = vmin[y] + x;
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];
                rinSum += sir[0];
                ginSum += sir[1];
                binSum += sir[2];
                rSum += rinSum;
                gSum += ginSum;
                bSum += binSum;
                stackPointer = (stackPointer + 1) % div;
                sir = stack[stackPointer];
                routSum += sir[0];
                goutSum += sir[1];
                boutSum += sir[2];
                rinSum -= sir[0];
                ginSum -= sir[1];
                binSum -= sir[2];
                yi += w;
            }
        }
        bitmap2.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap2;
    }

    /**
     * Glide 下载图片到目标路径（官行 1247–1263 + lambda 行 1530–1578）：
     * context 为 null 直接返回；否则 HeavyTaskUtil.executeNewTask 异步跑——
     * 地址串按 "||" 切成候选，逐个非空候选试同步下载，第一条成功即返回，
     * 失败 Timber.e 后试下一条（官 lambda 原样：成功 return、失败续试）。
     */
    public static void downloadImgByGlide(Context context, String str, String str2) {
        if (context == null) {
            return;
        }
        HeavyTaskUtil.executeNewTask(() -> {
            String[] split = str.split("\\|\\|");
            for (String s : split) {
                if (StringUtil.isNotEmpty(s)) {
                    try {
                        downloadImgByGlideSync(context, s, str2);
                        return;
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }
        });
    }

    /**
     * 同步下载本体（官行 1265–1359）：Glide downloadOnly 取缓存文件后
     * FileUtil.copy 到目标（目标已存在先删；不存在则父目录缺失时 mkdirs）；
     * 缓存文件为空或不存在抛 IOException("file not found")（官原样）。
     */
    private static void downloadImgByGlideSync(Context context, String str, String str2) throws ExecutionException, InterruptedException, IOException {
        File file = Glide.with(context).downloadOnly().load(str).submit().get();
        if (file != null && file.exists()) {
            File file2 = new File(str2);
            if (file2.exists()) {
                file2.delete();
            } else if (!file2.getParentFile().exists()) {
                file2.getParentFile().mkdirs();
            }
            FileUtil.copy(file, file2);
            return;
        }
        throw new IOException("file not found");
    }

    /**
     * 存相册入口（官行 486）：先经 XPermission 求 STORAGE 权限，准了才
     * 用 baseUrl 新构 PopImageLoaderNoView 走 saveBmpToAlbum；拒了弹
     * 「没有保存权限，保存功能无法使用！」。
     */
    public static void savePic2Gallery(Context context, String picUrl, String baseUrl, OnSaveListener listener) {
        XPermission.create(context, "STORAGE")
                .callback(new XPermission.SimpleCallback() {
                    @Override
                    public void onGranted() {
                        saveBmpToAlbum(context, new PopImageLoaderNoView(baseUrl),
                                new ArrayList<>(Collections.singletonList(picUrl)),
                                list -> {
                                    if (listener != null) {
                                        listener.success(list);
                                    }
                                });
                    }

                    @Override
                    public void onDenied() {
                        Toast.makeText(context, "没有保存权限，保存功能无法使用！", Toast.LENGTH_SHORT).show();
                    }
                })
                .request();
    }

    /** 存相册入口（官行 514）：多图版，完成回调直接透传。 */
    public static void savePic2Gallery(Context context, List<Object> picUrls, String baseUrl, Consumer<List<String>> completeListener) {
        XPermission.create(context, "STORAGE")
                .callback(new XPermission.SimpleCallback() {
                    @Override
                    public void onGranted() {
                        saveBmpToAlbum(context, new PopImageLoaderNoView(baseUrl), picUrls, completeListener);
                    }

                    @Override
                    public void onDenied() {
                        Toast.makeText(context, "没有保存权限，保存功能无法使用！", Toast.LENGTH_SHORT).show();
                    }
                })
                .request();
    }

    /** 存相册入口（官行 531）：外部给加载器版（查看器保存钮走此路）。 */
    public static void savePic2Gallery(Context context, XPopupImageLoader imageLoader, Object picUrl, OnSaveListener listener) {
        XPermission.create(context, "STORAGE")
                .callback(new XPermission.SimpleCallback() {
                    @Override
                    public void onGranted() {
                        saveBmpToAlbum(context, imageLoader,
                                new ArrayList<>(Collections.singletonList(picUrl)),
                                list -> {
                                    if (listener != null) {
                                        listener.success(list);
                                    }
                                });
                    }

                    @Override
                    public void onDenied() {
                        Toast.makeText(context, "没有保存权限，保存功能无法使用！", Toast.LENGTH_SHORT).show();
                    }
                })
                .request();
    }

    /**
     * 存相册本体（官行 554–631）：空名单直接回空结果；否则在新任务里
     * 逐图经加载器取源文件，按图片头判扩展名，文件名
     * 「日期-序号-md5(或时间戳).扩展名」落 Pictures 目录——SDK 29+ 走
     * MediaStore 插入＋框架 FileUtils 拷贝（插不进整件返回），低版本走
     * 直写文件＋媒体扫描；单图 IOException 记 Timber 并回主线程弹
     * 无权限提示，全部完成回主线程交 savedList。
     */
    public static void saveBmpToAlbum(Context context, XPopupImageLoader imageLoader, List<Object> uriList, Consumer<List<String>> completeListener) {
        if (CollectionUtil.isEmpty(uriList)) {
            if (completeListener != null) {
                completeListener.accept(new ArrayList<>());
            }
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        HeavyTaskUtil.executeNewTask(() -> {
            List<String> savedList = new ArrayList<>();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            for (int i = 0; i < uriList.size(); i++) {
                Object item = uriList.get(i);
                File source = imageLoader.getImageFile(context, item);
                if (source == null) {
                    continue;
                }
                String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_PICTURES;
                File dir = new File(dirPath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                try {
                    ImageHeaderParser.ImageType imageType = ImageHeaderParser.getImageType(new FileInputStream(source));
                    String ext = getFileExt(imageType);
                    String fileName = (item instanceof String ? StringUtil.md5((String) item) : String.valueOf(System.currentTimeMillis())) + "." + ext;
                    File target = new File(dirPath, dateStr + "-" + (i + 1) + "-" + fileName);
                    if (target.exists()) {
                        savedList.add(target.getAbsolutePath());
                        continue;
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues values = new ContentValues();
                        values.put("_display_name", target.getName());
                        values.put("mime_type", ShareUtil.getMimeTypeByExt(ext));
                        values.put("relative_path", Environment.DIRECTORY_PICTURES);
                        Uri inserted = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        if (inserted == null) {
                            return;
                        }
                        try (OutputStream outputStream = context.getContentResolver().openOutputStream(inserted)) {
                            FileInputStream inputStream = new FileInputStream(source);
                            FileUtils.copy(inputStream, outputStream);
                            inputStream.close();
                        } catch (IOException e) {
                            Timber.e(e);
                        }
                        File check = new File(target.getAbsolutePath());
                        if (!check.exists()) {
                            target.createNewFile();
                            writeFileFromIS(target, new FileInputStream(source));
                        }
                    } else {
                        target.createNewFile();
                        writeFileFromIS(target, new FileInputStream(source));
                        MediaScannerConnection.scanFile(context,
                                new String[]{target.getAbsolutePath()},
                                new String[]{"image/" + ext},
                                (path, uri) -> handler.post(() -> {
                                }));
                    }
                    savedList.add(target.getAbsolutePath());
                } catch (IOException e) {
                    Timber.e(e);
                    handler.post(() -> Toast.makeText(context, "没有保存权限，保存功能无法使用！", Toast.LENGTH_SHORT).show());
                }
            }
            if (completeListener != null) {
                handler.post(() -> completeListener.accept(savedList));
            }
        });
    }

    /** 流写文件（官行 639）：手写 try/catch/finally，失败回 false，关流失败只记 Timber。 */
    private static boolean writeFileFromIS(File file, InputStream is) {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            byte[] data = new byte[8192];
            int len;
            while ((len = is.read(data, 0, 8192)) != -1) {
                os.write(data, 0, len);
            }
        } catch (IOException e) {
            Timber.e(e);
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Timber.e(e);
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Timber.e(e);
                }
            }
        }
        return true;
    }

    /** 图片头类型转扩展名（官行 666）：GIF→gif、PNG/PNG_A→png、WEBP/WEBP_A→webp、余 jpeg。 */
    private static String getFileExt(ImageHeaderParser.ImageType imageType) {
        switch (imageType) {
            case GIF:
                return "gif";
            case PNG:
            case PNG_A:
                return "png";
            case WEBP:
            case WEBP_A:
                return "webp";
            default:
                return "jpeg";
        }
    }
}
