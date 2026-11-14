package com.example.hikerview.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.hikerview.ui.view.colorDialog.PromptDialog;
import com.fongmi.android.tv.App;
import com.lxj.xpopup.XPopup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.utils.BackupUtil} 的等价转写（官源
 * hikerView-source BackupUtil.java 直读＋官包行为对位）。
 *
 * <p>落点/命名/触发时机照官：备份目录＝UriUtils.getRootDir＋"/backup"；
 * 数据库备份名＝hiker_&lt;版本&gt;.db、数据库原名 hiker.db；规则文件打包
 * rules.zip（临时落 cache 目录）；总包 hiker.zip。触发点＝导入模式弹窗
 * 勾选「自动备份」时由 AutoImportHelper 的删除导入支在删规则前调用
 * backupDBAndJs(ctx,true)（官 importRulesWithDialog 监听同位），设置页的
 * 手动备份/恢复入口不在本簇文件面（接线点记分账）。
 *
 * <p>载体差记名（咱家无 LitePal/ZipUtils/JSManager/DebugUtil）：
 * ①官版本号取 LitePal 数据库版本，咱家 hk 数据以文件落盘、无对应版本，
 * {@link #DB_VERSION} 仅保持备份文件命名形，恢复提示因此省去官文案的
 * 版本号一句；②官 db 文件 hiker.db 在咱家不存在，backupDB 的「文件不
 * 存在」支官因 db 恒在从不触发，咱家同形静默返回（不逐次弹「数据库文件
 * 不存在」），恢复侧走官自带的 dbExist=false 分支文案；③ZipUtils 无 →
 * 本类内以 java.util.zip 同语义实现 zipFiles/unzipFile；④JSManager 无 →
 * recoveryDBAndJsNow 的旧版 JS 文件回拷支不落（rules.zip 支与模板文件
 * 支俱在）；⑤ShareUtil.findChooserToSend 无 → 分享支以 FileProvider＋
 * ACTION_SEND 选择器内联（AutoImportHelper.findToDealFileByPath 同先例）；
 * ⑥DebugUtil.showErrorMsg 无 → Timber＋ToastMgr 同文案承接。
 */
public class BackupUtil {

    private static final String TAG = "BackupUtil";

    /** 官版为 LitePal 数据库版本；咱家无对应物，仅作备份命名对位（见类注①）。 */
    private static final int DB_VERSION = 1;

    public static void backupDBAndJs(Context context, boolean silence) {
        BackupUtil.backupDB(context, true);
        List<String> filePaths = new ArrayList<>();
        String dbPath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + BackupUtil.getDBFileNameWithVersion();
        filePaths.add(dbPath);
        //第三方规则的文件
        String rulesZipFilePath = context.getCacheDir().getAbsolutePath() + File.separator + "rules.zip";
        List<String> ruleFiles = scanRuleFiles();
        if (ruleFiles != null && !ruleFiles.isEmpty()) {
            try {
                FileUtil.deleteFile(rulesZipFilePath);
                if (zipFiles(ruleFiles, rulesZipFilePath)) {
                    filePaths.add(rulesZipFilePath);
                }
            } catch (Exception e) {
                Timber.e(e, "rulesZip: ");
            }
        }
        String zipFilePath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + "hiker.zip";
        try {
            FileUtil.deleteFile(zipFilePath);
            if (zipFiles(filePaths, zipFilePath)) {
                if (!silence) {
                    ToastMgr.shortBottomCenter(context, "备份成功！");
                    findChooserToSend(context, "file://" + zipFilePath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper())
                    .post(() -> Toast
                            .makeText(App.get(), e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                            .show());
        }
    }

    public static List<String> scanRuleFiles() {
        List<String> ruleFiles = new ArrayList<>();
        String rulesPath = UriUtils.getRootDir(App.get()) + File.separator + "rules";
        File file = new File(rulesPath);
        if (!file.exists()) {
            return ruleFiles;
        }
        File[] files = file.listFiles();
        if (files != null && files.length > 0) {
            for (File file1 : files) {
                ruleFiles.add(file1.getAbsolutePath());
            }
        }
        return ruleFiles;
    }

    public static void backupDB(Context context, boolean silence) {
        File dbFile = context.getDatabasePath(getDBFileName());
        android.util.Log.d(TAG, "backupDB: path==>" + dbFile.getAbsolutePath());
        if (dbFile.exists()) {
            File exportDir = new File(UriUtils.getRootDir(context) + File.separator + "backup");
            if (!exportDir.exists()) {
                boolean ok = exportDir.mkdirs();
                if (!ok) {
                    ToastMgr.shortBottomCenter(context, "创建备份文件夹失败");
                    return;
                }
            }
            File backup = new File(exportDir, getDBFileNameWithVersion());
            try {
                if (backup.exists()) {
                    boolean del = backup.delete();
                    if (!del) {
                        ToastMgr.shortBottomCenter(context, "删除旧数据库文件失败");
                        return;
                    }
                }
                boolean ok = backup.createNewFile();
                if (!ok) {
                    ToastMgr.shortBottomCenter(context, "创建新数据库文件失败");
                    return;
                }
                FileUtil.copyFile(dbFile.getAbsolutePath(), backup.getAbsolutePath());
                if (!silence) {
                    ToastMgr.shortBottomCenter(context, "备份成功！");
                    findChooserToSend(context, "file://" + backup.getAbsolutePath());
                }
            } catch (Exception e) {
                Timber.e(e);
                ToastMgr.shortCenter(context, "数据库备份出错：" + e.getMessage());
            }
        }
        // 咱家无 hiker.db：官此支（toast「数据库文件不存在」）因官 db 恒在
        // 从不触发，咱家静默返回（类注②），不逐次打扰。
    }

    public static void backupDB(Context context) {
        backupDB(context, false);
    }

    public static void recoveryDBAndJsNow(Context context) {
        String zipFilePath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + "hiker.zip";
        try {
            String fileDirPath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + "hiker";
            FileUtil.deleteDirs(fileDirPath);
            new File(fileDirPath).mkdirs();
            unzipFile(zipFilePath, fileDirPath);
            String dbFilePath = fileDirPath + File.separator + BackupUtil.getDBFileNameWithVersion();
            File dbFile = new File(dbFilePath);
            String dbNewFilePath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + BackupUtil.getDBFileNameWithVersion();
            boolean dbExist = dbFile.exists();
            if (dbExist) {
                FileUtil.copyFile(dbFile.getAbsolutePath(), dbNewFilePath);
            }

            int fileNum = 0;
            File rulesFile = new File(fileDirPath + File.separator + "rules.zip");
            if (rulesFile.exists()) {
                //新版带规则文件
                String rulesDir = fileDirPath + File.separator + "rules";
                unzipFile(rulesFile.getAbsolutePath(), rulesDir);
                fileNum = FileUtil.getFileCount(rulesDir);
                FileUtil.copyDirectiory(UriUtils.getRootDir(App.get()) + File.separator + "rules", rulesDir);
            } else {
                File jsFile = new File(fileDirPath);
                File[] jsFiles = jsFile.listFiles();
                String templateFilePath = fileDirPath + File.separator + "rule_template.json";
                File templateFile = new File(templateFilePath);
                if (templateFile.exists()) {
                    String templateNewFilePath = UriUtils.getRootDir(context) + File.separator + "rules" + File.separator + "rule_template.json";
                    FileUtil.copyFile(templateFile.getAbsolutePath(), templateNewFilePath);
                }
                if (jsFiles != null) {
                    for (File jsFilePath : jsFiles) {
                        if (!jsFilePath.getName().endsWith(".js")) {
                            jsFilePath.delete();
                        } else {
                            fileNum++;
                        }
                    }
                }
                // 官此后 deleteDirs(JSManager.getJsDirPath())＋copyDirectiory 回拷
                // JS 文件：咱家无 JSManager/JS 目录面，该支不落（类注④）。
            }
            int finalFileNum = fileNum;
            String title;
            if (!dbExist) {
                title = "已恢复" + finalFileNum + "个应用插件，没有获取到适合当前版本的db文件";
            } else {
                title = "已恢复" + finalFileNum + "个应用插件，是否立即恢复db文件（包括小程序规则、历史记录、收藏等）？";
            }
            new XPopup.Builder(context)
                    .asConfirm("恢复完成", title, () -> {
                        if (dbExist) {
                            BackupUtil.recoveryDBNow(context);
                        }
                    }).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void recoveryDBAndJs(Context context) {
        String zipFilePath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + "hiker.zip";
        new PromptDialog(context)
                .setTitleText("风险提示")
                .setSpannedContentByStr("确定从备份文件（" + zipFilePath + "）恢复数据吗？注意“如果备份和恢复时的数据库版本不一致会导致软件启动闪退！”")
                .setPositiveListener("确定恢复", dialog1 -> {
                    dialog1.dismiss();
                    recoveryDBAndJsNow(context);
                }).show();
    }

    public static void recoveryDB(Context context) {
        String filePath = UriUtils.getRootDir(context) + File.separator + "backup" + File.separator + getDBFileNameWithVersion();
        new PromptDialog(context)
                .setTitleText("风险提示")
                .setSpannedContentByStr("确定从备份文件（" + filePath + "）恢复数据吗？注意“如果备份和恢复时的数据库版本不一致会导致软件启动闪退！”")
                .setPositiveListener("确定恢复", dialog1 -> {
                    dialog1.dismiss();
                    recoveryDBNow(context);
                }).show();
    }

    public static void recoveryDBNow(Context context) {
        // 官此法 synchronized(LitePal.class) 内先 close 数据库再换文件；
        // 咱家无 LitePal 数据库可关（类注②），换文件本体照官。
        File exportDir = new File(UriUtils.getRootDir(context) + File.separator + "backup");
        if (!exportDir.exists()) {
            boolean ok = exportDir.mkdirs();
            if (!ok) {
                ToastMgr.shortBottomCenter(context, "创建备份文件夹失败");
                return;
            }
        }
        File backup = new File(exportDir, getDBFileNameWithVersion());
        if (backup.exists()) {
            File dbFile = context.getDatabasePath(getDBFileName());
            if (dbFile.exists()) {
                boolean del = dbFile.delete();
                if (!del) {
                    ToastMgr.shortBottomCenter(context, "删除旧数据库文件失败");
                    return;
                }
            }
            try {
                boolean ok = dbFile.createNewFile();
                if (!ok) {
                    ToastMgr.shortBottomCenter(context, "创建新数据库文件失败");
                    return;
                }
                FileUtil.copyFile(backup.getAbsolutePath(), dbFile.getAbsolutePath());
                new PromptDialog(context)
                        .setDialogType(PromptDialog.DIALOG_TYPE_SUCCESS)
                        .setContentText("从备份恢复成功！开始重启软件使生效！")
                        .setTheCancelable(false)
                        .setAnimationEnable(true)
                        .setTitleText("温馨提示")
                        .setPositiveListener("立即重启", dialog -> {
                            dialog.dismiss();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        }).show();
            } catch (Exception e) {
                Timber.e(e);
                ToastMgr.shortCenter(context, "数据库恢复备份出错：" + e.getMessage());
            }
        } else {
            ToastMgr.shortBottomCenter(context, backup.getAbsolutePath() + "数据库文件不存在");
        }
    }

    public static String getDBFileNameWithVersion() {
        return "hiker_" + DB_VERSION + ".db";
    }

    private static String getDBFileName() {
        return "hiker.db";
    }

    /** 官 ShareUtil.findChooserToSend 的咱家内联（类注⑤）：ACTION_SEND 带文件拉起选择器。 */
    private static void findChooserToSend(Context context, String fileUri) {
        String path = fileUri.startsWith("file://") ? fileUri.substring("file://".length()) : fileUri;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(path));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        context.startActivity(Intent.createChooser(intent, "请选择应用"));
    }

    /** 官 ZipUtils.zipFiles 同语义（类注③）：逐个文件/目录（递归）打包，缺失条目跳过。 */
    private static boolean zipFiles(List<String> filePaths, String zipFilePath) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
            for (String path : filePaths) {
                File file = new File(path);
                if (!file.exists()) {
                    continue;
                }
                addToZip(zos, file, file.getName());
            }
            zos.finish();
            return true;
        } catch (Exception e) {
            Timber.e(e, "zipFiles: ");
            return false;
        }
    }

    private static void addToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                addToZip(zos, child, entryName + "/" + child.getName());
            }
            return;
        }
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    /** 官 ZipUtils.unzipFile 同语义（类注③）：解压到目标目录，穿越条目跳过。 */
    private static void unzipFile(String zipFilePath, String outDir) throws IOException {
        File outRoot = new File(outDir);
        if (!outRoot.exists()) {
            outRoot.mkdirs();
        }
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("/") && !name.contains("..")) {
                    File out = new File(outRoot, name);
                    if (entry.isDirectory()) {
                        out.mkdirs();
                    } else {
                        File parent = out.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            int len;
                            while ((len = zin.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
                zin.closeEntry();
            }
        }
    }
}
