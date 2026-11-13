package com.fongmi.android.tv.api.loader;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 潇洒包（本地仓 pkg3）spider.jar 的宿主身份门面（校验链账 pkg3-whitelist-chain-20261031）。
 *
 * <p>该 jar 的 Init.init 开头有两道串联白名单门：入参 Context.getPackageName() 对 48 个
 * 包名名单整名比对、再按这个名经 PackageManager 查应用名对第二份名单比对，任一不过
 * 5 秒后 killProcess 杀宿主进程；校验段不查签名证书、不枚举已装应用，比对对象 100%
 * 是「递进来的 Context 自报的包名＋PM 按这个名查出的 label」。本门面只在这只 jar
 * （文件长度＋SHA-256 与验账实物逐字对上时）被 JarLoader.invokeInit 递给 Init.init：
 * 它是 Application 子类实例（过 init 内的 check-cast），base 挂真 App 全量委托，
 * 只覆写 getPackageName 报白名单内的 com.fongmi.android.tv、getPackageManager 递
 * PmFacade 让 label 答白名单内的「影视仓」。其它 jar 蜘蛛一律仍递真 App，行为零变化。
 *
 * <p>调用方纪律：create() 每次新建一个门面实例，只许递给 pkg3 这一只 jar 的
 * Init.init；蜘蛛实例自身的 Spider.init 仍递真 App（白名单校验只在 Init.init）。
 */
public final class Pkg3HostFacade extends Application {

    /** 验账实物 pkg3-spider.jar 的文件长度与 SHA-256（与 PanProxyCompat 同款内容键）。 */
    static final long JAR_LENGTH = 4843928L;
    static final String JAR_SHA256 = "e8eb518de73adad78bd6df23b6e509e4af2ca416b7bee354d81cf85bf22a8d66";
    /** 白名单 #1（包名）内的报称，与名单 #2（应用名）内的报称 label。 */
    static final String REPORT_PACKAGE = "com.fongmi.android.tv";
    static final String REPORT_LABEL = "影视仓";

    private final PackageManager packageManager;

    private Pkg3HostFacade(PackageManager real) {
        this.packageManager = new PmFacade(real);
    }

    /** 这只 jar 是不是验账的那只潇洒包：先比长度、再比 SHA-256，任何异常按不匹配处理。 */
    static boolean matches(File jar) {
        try {
            if (jar == null || jar.length() != JAR_LENGTH) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(jar)) {
                byte[] buffer = new byte[16384];
                for (int count; (count = in.read(buffer)) != -1; ) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return JAR_SHA256.contentEquals(hex);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 新建门面并挂上真 App 作 base；之后 Context 方法除覆写两项外全量委托真 App。 */
    public static Pkg3HostFacade create() {
        App app = App.get();
        Pkg3HostFacade facade = new Pkg3HostFacade(app.getPackageManager());
        facade.attachBaseContext(app);
        return facade;
    }

    @Override
    public String getPackageName() {
        return REPORT_PACKAGE;
    }

    @Override
    public PackageManager getPackageManager() {
        return packageManager;
    }

    // Application 的回调登记面不走 ContextWrapper 委托（是 Application 自有状态），
    // 在此转交真 App，避免蜘蛛经 Init 存档的 Context 登记的回调永不触发。
    @Override
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        App.get().registerActivityLifecycleCallbacks(callback);
    }

    @Override
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        App.get().unregisterActivityLifecycleCallbacks(callback);
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        App.get().registerComponentCallbacks(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        App.get().unregisterComponentCallbacks(callback);
    }

    @Override
    public void registerOnProvideAssistDataListener(Application.OnProvideAssistDataListener callback) {
        App.get().registerOnProvideAssistDataListener(callback);
    }

    @Override
    public void unregisterOnProvideAssistDataListener(Application.OnProvideAssistDataListener callback) {
        App.get().unregisterOnProvideAssistDataListener(callback);
    }

    /**
     * PM 门面：只拦校验段用到的两处——按报称包名查 ApplicationInfo 时造一份假的、
     * 取 label 时回名单内的「影视仓」；其余方法全量透传真 PM（委托方法由
     * android-36 PackageManager 抽象方法全集生成，javac 强制完整）。
     */
    static final class PmFacade extends PackageManager {

        private final PackageManager real;

        PmFacade(PackageManager real) {
            this.real = real;
        }

        @Override
        public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
            if (REPORT_PACKAGE.equals(packageName)) return fabricatedInfo();
            return real.getApplicationInfo(packageName, flags);
        }

        @Override
        public CharSequence getApplicationLabel(ApplicationInfo info) {
            if (info != null && REPORT_PACKAGE.equals(info.packageName)) return REPORT_LABEL;
            return real.getApplicationLabel(info);
        }

        private static ApplicationInfo fabricatedInfo() {
            ApplicationInfo info = new ApplicationInfo();
            info.packageName = REPORT_PACKAGE;
            info.nonLocalizedLabel = REPORT_LABEL;
            info.labelRes = 0;
            info.name = "com.fongmi.android.tv.App";
            info.enabled = true;
            return info;
        }
        @Override
        public void addPackageToPreferred(java.lang.String p0) {
            real.addPackageToPreferred(p0);
        }

        @Override
        public boolean addPermission(android.content.pm.PermissionInfo p0) {
            return real.addPermission(p0);
        }

        @Override
        public boolean addPermissionAsync(android.content.pm.PermissionInfo p0) {
            return real.addPermissionAsync(p0);
        }

        @Override
        public void addPreferredActivity(android.content.IntentFilter p0, int p1, android.content.ComponentName[] p2, android.content.ComponentName p3) {
            real.addPreferredActivity(p0, p1, p2, p3);
        }

        @Override
        public boolean canRequestPackageInstalls() {
            return real.canRequestPackageInstalls();
        }

        @Override
        public java.lang.String[] canonicalToCurrentPackageNames(java.lang.String[] p0) {
            return real.canonicalToCurrentPackageNames(p0);
        }

        @Override
        public int checkPermission(java.lang.String p0, java.lang.String p1) {
            return real.checkPermission(p0, p1);
        }

        @Override
        public int checkSignatures(int p0, int p1) {
            return real.checkSignatures(p0, p1);
        }

        @Override
        public int checkSignatures(java.lang.String p0, java.lang.String p1) {
            return real.checkSignatures(p0, p1);
        }

        @Override
        public void clearInstantAppCookie() {
            real.clearInstantAppCookie();
        }

        @Override
        public void clearPackagePreferredActivities(java.lang.String p0) {
            real.clearPackagePreferredActivities(p0);
        }

        @Override
        public java.lang.String[] currentToCanonicalPackageNames(java.lang.String[] p0) {
            return real.currentToCanonicalPackageNames(p0);
        }

        @Override
        public void extendVerificationTimeout(int p0, int p1, long p2) {
            real.extendVerificationTimeout(p0, p1, p2);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityBanner(android.content.ComponentName p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityBanner(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityBanner(android.content.Intent p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityBanner(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityIcon(android.content.ComponentName p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityIcon(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityIcon(android.content.Intent p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityIcon(p0);
        }

        @Override
        public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityInfo(p0, p1);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityLogo(android.content.ComponentName p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityLogo(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getActivityLogo(android.content.Intent p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getActivityLogo(p0);
        }

        @Override
        public java.util.List<android.content.pm.PermissionGroupInfo> getAllPermissionGroups(int p0) {
            return real.getAllPermissionGroups(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationBanner(android.content.pm.ApplicationInfo p0) {
            return real.getApplicationBanner(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationBanner(java.lang.String p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getApplicationBanner(p0);
        }

        @Override
        public int getApplicationEnabledSetting(java.lang.String p0) {
            return real.getApplicationEnabledSetting(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationIcon(android.content.pm.ApplicationInfo p0) {
            return real.getApplicationIcon(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationIcon(java.lang.String p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getApplicationIcon(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationLogo(android.content.pm.ApplicationInfo p0) {
            return real.getApplicationLogo(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getApplicationLogo(java.lang.String p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getApplicationLogo(p0);
        }

        @Override
        public android.content.pm.ChangedPackages getChangedPackages(int p0) {
            return real.getChangedPackages(p0);
        }

        @Override
        public int getComponentEnabledSetting(android.content.ComponentName p0) {
            return real.getComponentEnabledSetting(p0);
        }

        @Override
        public android.graphics.drawable.Drawable getDefaultActivityIcon() {
            return real.getDefaultActivityIcon();
        }

        @Override
        public android.graphics.drawable.Drawable getDrawable(java.lang.String p0, int p1, android.content.pm.ApplicationInfo p2) {
            return real.getDrawable(p0, p1, p2);
        }

        @Override
        public java.util.List<android.content.pm.ApplicationInfo> getInstalledApplications(int p0) {
            return real.getInstalledApplications(p0);
        }

        @Override
        public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(int p0) {
            return real.getInstalledPackages(p0);
        }

        @Override
        public java.lang.String getInstallerPackageName(java.lang.String p0) {
            return real.getInstallerPackageName(p0);
        }

        @Override
        public byte[] getInstantAppCookie() {
            return real.getInstantAppCookie();
        }

        @Override
        public int getInstantAppCookieMaxBytes() {
            return real.getInstantAppCookieMaxBytes();
        }

        @Override
        public android.content.pm.InstrumentationInfo getInstrumentationInfo(android.content.ComponentName p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getInstrumentationInfo(p0, p1);
        }

        @Override
        public android.content.Intent getLaunchIntentForPackage(java.lang.String p0) {
            return real.getLaunchIntentForPackage(p0);
        }

        @Override
        public android.content.Intent getLeanbackLaunchIntentForPackage(java.lang.String p0) {
            return real.getLeanbackLaunchIntentForPackage(p0);
        }

        @Override
        public java.lang.String getNameForUid(int p0) {
            return real.getNameForUid(p0);
        }

        @Override
        public int[] getPackageGids(java.lang.String p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPackageGids(p0);
        }

        @Override
        public int[] getPackageGids(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPackageGids(p0, p1);
        }

        @Override
        public android.content.pm.PackageInfo getPackageInfo(android.content.pm.VersionedPackage p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPackageInfo(p0, p1);
        }

        @Override
        public android.content.pm.PackageInfo getPackageInfo(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPackageInfo(p0, p1);
        }

        @Override
        public android.content.pm.PackageInstaller getPackageInstaller() {
            return real.getPackageInstaller();
        }

        @Override
        public int getPackageUid(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPackageUid(p0, p1);
        }

        @Override
        public java.lang.String[] getPackagesForUid(int p0) {
            return real.getPackagesForUid(p0);
        }

        @Override
        public java.util.List<android.content.pm.PackageInfo> getPackagesHoldingPermissions(java.lang.String[] p0, int p1) {
            return real.getPackagesHoldingPermissions(p0, p1);
        }

        @Override
        public android.content.pm.PermissionGroupInfo getPermissionGroupInfo(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPermissionGroupInfo(p0, p1);
        }

        @Override
        public android.content.pm.PermissionInfo getPermissionInfo(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getPermissionInfo(p0, p1);
        }

        @Override
        public int getPreferredActivities(java.util.List<android.content.IntentFilter> p0, java.util.List<android.content.ComponentName> p1, java.lang.String p2) {
            return real.getPreferredActivities(p0, p1, p2);
        }

        @Override
        public java.util.List<android.content.pm.PackageInfo> getPreferredPackages(int p0) {
            return real.getPreferredPackages(p0);
        }

        @Override
        public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getProviderInfo(p0, p1);
        }

        @Override
        public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getReceiverInfo(p0, p1);
        }

        @Override
        public android.content.res.Resources getResourcesForActivity(android.content.ComponentName p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getResourcesForActivity(p0);
        }

        @Override
        public android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getResourcesForApplication(p0);
        }

        @Override
        public android.content.res.Resources getResourcesForApplication(java.lang.String p0) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getResourcesForApplication(p0);
        }

        @Override
        public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.getServiceInfo(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.SharedLibraryInfo> getSharedLibraries(int p0) {
            return real.getSharedLibraries(p0);
        }

        @Override
        public android.content.pm.FeatureInfo[] getSystemAvailableFeatures() {
            return real.getSystemAvailableFeatures();
        }

        @Override
        public java.lang.String[] getSystemSharedLibraryNames() {
            return real.getSystemSharedLibraryNames();
        }

        @Override
        public java.lang.CharSequence getText(java.lang.String p0, int p1, android.content.pm.ApplicationInfo p2) {
            return real.getText(p0, p1, p2);
        }

        @Override
        public android.graphics.drawable.Drawable getUserBadgedDrawableForDensity(android.graphics.drawable.Drawable p0, android.os.UserHandle p1, android.graphics.Rect p2, int p3) {
            return real.getUserBadgedDrawableForDensity(p0, p1, p2, p3);
        }

        @Override
        public android.graphics.drawable.Drawable getUserBadgedIcon(android.graphics.drawable.Drawable p0, android.os.UserHandle p1) {
            return real.getUserBadgedIcon(p0, p1);
        }

        @Override
        public java.lang.CharSequence getUserBadgedLabel(java.lang.CharSequence p0, android.os.UserHandle p1) {
            return real.getUserBadgedLabel(p0, p1);
        }

        @Override
        public android.content.res.XmlResourceParser getXml(java.lang.String p0, int p1, android.content.pm.ApplicationInfo p2) {
            return real.getXml(p0, p1, p2);
        }

        @Override
        public boolean hasSystemFeature(java.lang.String p0) {
            return real.hasSystemFeature(p0);
        }

        @Override
        public boolean hasSystemFeature(java.lang.String p0, int p1) {
            return real.hasSystemFeature(p0, p1);
        }

        @Override
        public boolean isInstantApp() {
            return real.isInstantApp();
        }

        @Override
        public boolean isInstantApp(java.lang.String p0) {
            return real.isInstantApp(p0);
        }

        @Override
        public boolean isPermissionRevokedByPolicy(java.lang.String p0, java.lang.String p1) {
            return real.isPermissionRevokedByPolicy(p0, p1);
        }

        @Override
        public boolean isSafeMode() {
            return real.isSafeMode();
        }

        @Override
        public java.util.List<android.content.pm.ResolveInfo> queryBroadcastReceivers(android.content.Intent p0, int p1) {
            return real.queryBroadcastReceivers(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.ProviderInfo> queryContentProviders(java.lang.String p0, int p1, int p2) {
            return real.queryContentProviders(p0, p1, p2);
        }

        @Override
        public java.util.List<android.content.pm.InstrumentationInfo> queryInstrumentation(java.lang.String p0, int p1) {
            return real.queryInstrumentation(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent p0, int p1) {
            return real.queryIntentActivities(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.ResolveInfo> queryIntentActivityOptions(android.content.ComponentName p0, android.content.Intent[] p1, android.content.Intent p2, int p3) {
            return real.queryIntentActivityOptions(p0, p1, p2, p3);
        }

        @Override
        public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders(android.content.Intent p0, int p1) {
            return real.queryIntentContentProviders(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(android.content.Intent p0, int p1) {
            return real.queryIntentServices(p0, p1);
        }

        @Override
        public java.util.List<android.content.pm.PermissionInfo> queryPermissionsByGroup(java.lang.String p0, int p1) throws android.content.pm.PackageManager.NameNotFoundException {
            return real.queryPermissionsByGroup(p0, p1);
        }

        @Override
        public void removePackageFromPreferred(java.lang.String p0) {
            real.removePackageFromPreferred(p0);
        }

        @Override
        public void removePermission(java.lang.String p0) {
            real.removePermission(p0);
        }

        @Override
        public android.content.pm.ResolveInfo resolveActivity(android.content.Intent p0, int p1) {
            return real.resolveActivity(p0, p1);
        }

        @Override
        public android.content.pm.ProviderInfo resolveContentProvider(java.lang.String p0, int p1) {
            return real.resolveContentProvider(p0, p1);
        }

        @Override
        public android.content.pm.ResolveInfo resolveService(android.content.Intent p0, int p1) {
            return real.resolveService(p0, p1);
        }

        @Override
        public void setApplicationCategoryHint(java.lang.String p0, int p1) {
            real.setApplicationCategoryHint(p0, p1);
        }

        @Override
        public void setApplicationEnabledSetting(java.lang.String p0, int p1, int p2) {
            real.setApplicationEnabledSetting(p0, p1, p2);
        }

        @Override
        public void setComponentEnabledSetting(android.content.ComponentName p0, int p1, int p2) {
            real.setComponentEnabledSetting(p0, p1, p2);
        }

        @Override
        public void setInstallerPackageName(java.lang.String p0, java.lang.String p1) {
            real.setInstallerPackageName(p0, p1);
        }

        @Override
        public void updateInstantAppCookie(byte[] p0) {
            real.updateInstantAppCookie(p0);
        }

        @Override
        public void verifyPendingInstall(int p0, int p1) {
            real.verifyPendingInstall(p0, p1);
        }
    }
}
