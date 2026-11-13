package com.example.hikerview.utils;

import com.alibaba.fastjson.JSON;
import com.fongmi.android.tv.App;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.utils.DataTransferUtils}（Kotlin object）
 * 照官 smali 全类转写（官源 smali_classes2/com/example/hikerview/utils/
 * DataTransferUtils.smali）：进程内 temp/cache/pics 三槽 + cacheDir 下
 * 「rule-&lt;名&gt;.txt」文件持久化（put 系经 HeavyTaskUtil 后台 fastjson
 * 序列化落盘、load 系内存命中直取并清槽、未命中读文件反序列化）。
 * 官 Application.application 取 cacheDir 接咱家 App.get()（本树既有对位）。
 * 官 Kotlin reified 泛型 loadCache/loadTemp 的 instanceof T 检查在字节码
 * 里即 instanceof Object（恒真），Java 形以泛型方法＋未检查转型同效落。
 * clearCache 首段的时间比较官原文取的是缓存目录本身的 lastModified
 * （非逐文件），照官保留不改。
 */
public final class DataTransferUtils {

    public static final DataTransferUtils INSTANCE = new DataTransferUtils();

    private static Object cache;
    private static String cacheFile;
    private static ArrayList<String> pics;
    private static Object temp;

    private DataTransferUtils() {
    }

    public Object getCache() {
        return cache;
    }

    public void setCache(Object obj) {
        cache = obj;
    }

    public String getCacheFile() {
        return cacheFile;
    }

    public void setCacheFile(String str) {
        cacheFile = str;
    }

    public ArrayList<String> getPics() {
        return pics;
    }

    public void setPics(ArrayList<String> arrayList) {
        pics = arrayList;
    }

    public Object getTemp() {
        return temp;
    }

    public void setTemp(Object obj) {
        temp = obj;
    }

    public void putTemp(final Object t) {
        if (t == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putTemp, parameter t");
        }
        temp = t;
        HeavyTaskUtil.executeNewTask(() -> {
            try {
                INSTANCE.saveCache(JSON.toJSONString(t), "temp");
            } catch (Exception ignored) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T loadTemp() {
        Object t = getTemp();
        if (t != null) {
            setTemp(null);
            return (T) t;
        }
        String text = readFromCache("temp");
        if (text != null && text.length() != 0) {
            try {
                return (T) JSON.parseObject(text, Object.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void putPics(final ArrayList<String> t) {
        if (t == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putPics, parameter t");
        }
        pics = t;
        HeavyTaskUtil.executeNewTask(() -> {
            try {
                INSTANCE.saveCache(JSON.toJSONString(t), "pics");
            } catch (Exception ignored) {
            }
        });
    }

    public List<String> loadPics() {
        ArrayList<String> list = pics;
        if (list != null) {
            pics = null;
            return list;
        }
        String text = readFromCache("pics");
        if (text != null && text.length() != 0) {
            try {
                return JSON.parseArray(text, String.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void putCache(final Object t, final String file) {
        if (t == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putCache, parameter t");
        }
        if (file == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putCache, parameter file");
        }
        cache = t;
        cacheFile = file;
        HeavyTaskUtil.executeNewTask(() -> {
            try {
                INSTANCE.saveCache(JSON.toJSONString(t), file);
            } catch (Exception ignored) {
            }
        });
    }

    public void putCacheString(String t, String file) {
        if (t == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putCacheString, parameter t");
        }
        if (file == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.putCacheString, parameter file");
        }
        putCache(t, file);
    }

    @SuppressWarnings("unchecked")
    public <T> T loadCache(String file) {
        if (file == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.loadCache, parameter file");
        }
        Object t = getCache();
        if (t != null && file.equals(getCacheFile())) {
            setCache(null);
            setCacheFile(null);
            return (T) t;
        }
        String text = readFromCache(file);
        if (text != null && text.length() != 0) {
            try {
                return (T) JSON.parseObject(text, Object.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String loadCacheString(String file) {
        if (file == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method com/example/hikerview/utils/DataTransferUtils.loadCacheString, parameter file");
        }
        Object t = getCache();
        if (t != null && t instanceof String && file.equals(getCacheFile())) {
            String str = (String) t;
            setCache(null);
            setCacheFile(null);
            return str;
        }
        String text = readFromCache(file);
        String result = null;
        if (text != null && text.length() != 0) {
            try {
                result = JSON.parseObject(text, String.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private void saveCache(String data, String file) {
        String path = App.get().getCacheDir().toString() + File.separator + "rule-" + file + ".txt";
        Timber.d("setTempRuleData path: %s", path);
        FileUtil.stringToFile(data, path);
    }

    public String readFromCache(String file) {
        if (file != null && file.length() != 0) {
            HeavyTaskUtil.executeNewTask(INSTANCE::clearCache);
            String path = App.get().getCacheDir().toString() + File.separator + "rule-" + file + ".txt";
            if (new File(path).exists()) {
                return FileUtil.fileToString(path);
            }
        }
        return null;
    }

    private void clearCache() {
        String cacheDir = App.get().getCacheDir().toString();
        File dir = new File(cacheDir);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                long now = System.currentTimeMillis();
                for (File f : files) {
                    if (f.isFile() && f.getName().startsWith("rule-") && f.getName().endsWith(".txt")
                            && now - dir.lastModified() > 86400000L) {
                        f.delete();
                    }
                }
            }
            File[] files2 = dir.listFiles();
            if (files2 != null && files2.length != 0 && files2.length > 100) {
                List<File> ruleFiles = new ArrayList<>();
                for (File f : files2) {
                    if (f.isFile() && f.getName().startsWith("rule-") && f.getName().endsWith(".txt")) {
                        ruleFiles.add(f);
                    }
                }
                if (ruleFiles.size() > 100) {
                    ruleFiles.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int i = 0; i < ruleFiles.size(); i++) {
                        if (i >= 100) {
                            ruleFiles.get(i).delete();
                        }
                    }
                }
            }
        }
    }
}
