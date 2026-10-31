#!/usr/bin/env bash
# 朋友版专用：构建前把「第三方红果」整套从源码里删掉（不是关掉）。
# 只在 CI 的临时检出上执行，仓库源码不动。任何一处对不上就让构建失败，
# 防止哪天重构改了写法、朋友版悄悄带着红果发出去（与 strip-friend-seed.sh 同款纪律）。
#
# 本脚本 2026-10-30 随红果短剧全删重建同步重写：登录全套在重建阶段1
# 已从正式版源码整删（LoginManager 无任何红果注册/桥，本脚本不再对它动刀，
# 只在复查二里验它别家提供方完好）；短剧与小说同剥（旧口径不变）。
#
# 删的只有本功能的东西：
#   - 红果本体包 app/src/main/java/com/fongmi/android/tv/hongguo/（api/book/crypto/
#     drama/model/proxy/sign/ui 全包，短剧与小说同在内）与离线单测 app/src/test/hongguo/；
#   - mobile 源集的红果两目录（book 浏览页 + HongguoNovelDetailActivity）；
#   - 本体资源（main 源集）：ic_drama_* 图标 16 份（重建新页自绘）、小说面共用
#     图形 5 份＋ic_hg_search_gray＋item_hongguo_search_book.xml（官方版里是小说
#     线共用件、保留；朋友版小说同剥、无人引用，一并删）；
#   - Nano 本地服务器的红果代理解密 process（HongguoStream 本体＋Nano 的 import 与注册行）；
#   - 底栏短剧格：HomeActivity 的 import／注册行／点击分发支／启动直达块、
#     isHongguoTabEnabled 改回 false（格子不注册）、ic_nav_duanju 双态图标、nav_duanju 字符串；
#   - Manifest（mobile）里红果十个 Activity＋书城浏览页＋红果听书播放页
#     （2026-10-30 听书分家新增 HgAudioPlayActivity）＋红果听书播放服务
#     （2026-10-31 W-15 拆套新增 HgAudioPlaybackService，1.4.69 补剥）；
#   - 2026-10-31 签名/登录批新增：短信登录页 HongguoLoginActivity 的 Manifest
#     登记、我的页五格字形 ic_mine_* 四份、原版签名三件（jniLibs 的
#     libmetasec_ml.so、assets/hg-metasec/metasec.dex、Covode 桩）。
#   - 2026-10-31 阅读分家批（1.4.69）：阅读书架 ReadingShelfActivity 里新挂的
#     红果组三卡（hgNovelHistoryCard／hgAudioEntryCard／hgAudioListeningCard
#     三个方法块＋组标题与三行 addView 调用块），连同只供红果听书播放页
#     「回书架」用的带签位 start 与 EXTRA_TAB／onCreate 读签段（全树唯一
#     调用方就是被剥的 HgAudioPlayActivity，普通 start 与其调用点不动）；
#     剥后该文件须与三卡落码前逐字节全等（出包账 build-169 本地实跑验证）。
#
# 不许误伤的共用件（海阔规则年代就有的壳子件，只是名字里带 Hongguo，与本功能无关，留）：
#   - com.fongmi.android.tv.api.HongguoCommentApi / HongguoDanmakuApi / FqCommentSigner
#     （点播播放链的红果评论/弹幕抓取，新评论面也在用）；
#   - hongguo_panel_* 动画、VideoActivity 的红果评论/弹幕链、Danmaku 的 hongguo 平台名；
#   - 注：mobile 的 HongguoCommentDialog / HongguoCommentAdapter 与 dialog/adapter
#     布局、shape_hongguo_*、hongguo_comment* 字符串已于 2026-10-31 W-07 随旧轨
#     整套从正式版源码删除（不在剥离范围、复查二不再断言）。
#   - 登录框架本体（LoginManager 注册表与通用口、各提供方）、
#     底栏组件本体（XcBottomBar/BottomTabs 与 ids.xml 的 hongguo 预声明 id——无害占位）、
#     Nano 其余 process。
# 终检只认本功能专属标识（tv.hongguo 包名与十个 Activity 名、HongguoStream、
# ic_nav_duanju/nav_duanju、ic_drama_），不认泛 Hongguo 字样，
# 免得把上面的共用件误报成残留。
set -euo pipefail

fail() { echo "::error title=朋友版删红果失败::$1"; exit 1; }

HGPKG=app/src/main/java/com/fongmi/android/tv/hongguo
HGTEST=app/src/test/hongguo
STREAM=app/src/main/java/com/fongmi/android/tv/server/process/HongguoStream.java
NANO=app/src/main/java/com/fongmi/android/tv/server/Nano.java
HOME=app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java
MANIFEST=app/src/mobile/AndroidManifest.xml
SHELF=app/src/mobile/java/com/fongmi/android/tv/ui/activity/ReadingShelfActivity.java
MSTRINGS=app/src/mobile/res/values/strings.xml
LM=app/src/main/java/com/fongmi/android/tv/login/LoginManager.java

for f in "$STREAM" "$NANO" "$HOME" "$MANIFEST" "$SHELF" "$MSTRINGS" "$LM"; do
  [ -f "$f" ] || fail "红果剥离目标文件找不到：$f"
done
[ -d "$HGPKG" ] || fail "红果本体包找不到：$HGPKG"
[ -d "$HGTEST" ] || fail "红果离线单测目录找不到：$HGTEST"
[ -d app/src/mobile/java/com/fongmi/android/tv/hongguo/book ] || fail "mobile 红果 book 目录找不到"
[ -d app/src/mobile/java/com/fongmi/android/tv/hongguo/ui ] || fail "mobile 红果 ui 目录找不到"

# ---- 锚点预检：改一处写法就先在这里炸，不许带着对不上硬删 ----
grep -qF "import com.fongmi.android.tv.server.process.HongguoStream;" "$NANO" || fail "Nano 的 HongguoStream import 对不上"
grep -qF "process.add(new HongguoStream());" "$NANO" || fail "Nano 的 HongguoStream 注册行对不上"
grep -qF "import com.fongmi.android.tv.hongguo.ui.HongguoFeedActivity;" "$HOME" || fail "HomeActivity 的红果 import 对不上"
grep -qF "tabs.add(new BottomTabs.Spec(R.id.hongguo, R.drawable.ic_nav_duanju_outline, R.drawable.ic_nav_duanju_filled, getString(R.string.nav_duanju), true));" "$HOME" || fail "HomeActivity 的短剧格注册行对不上"
grep -qF "if (itemId == R.id.hongguo) {" "$HOME" || fail "HomeActivity 的短剧格点击分发对不上"
grep -qF "红果「启动直达短剧」" "$HOME" || fail "HomeActivity 的启动直达短剧块对不上"
grep -qF "<string name=\"nav_duanju\">短剧</string>" "$MSTRINGS" || fail "短剧格字符串 nav_duanju 对不上"
# LoginManager 必须仍是零红果（重建阶段1 已整删；此处防回潮，带回来就先炸）
if grep -q "Hongguo" "$LM"; then
  fail "LoginManager 出现红果字样（重建后应为零，疑回潮）"
fi
for a in HongguoFeedActivity HongguoDetailActivity HongguoTheaterActivity HongguoSearchActivity HongguoAuthorActivity HongguoPostActivity HongguoMineActivity HongguoMineEditActivity HongguoLandActivity HongguoNovelDetailActivity HongguoLoginActivity; do
  grep -qF "android:name=\"com.fongmi.android.tv.hongguo.ui.$a\"" "$MANIFEST" || fail "Manifest 的红果登记对不上：$a"
done
grep -qF "android:name=\"com.fongmi.android.tv.hongguo.book.HgBookBrowseActivity\"" "$MANIFEST" || fail "Manifest 的红果书城浏览页登记对不上"
grep -qF "android:name=\"com.fongmi.android.tv.hongguo.book.HgAudioPlayActivity\"" "$MANIFEST" || fail "Manifest 的红果听书播放页登记对不上"
grep -qF "android:name=\"com.fongmi.android.tv.hongguo.book.HgAudioPlaybackService\"" "$MANIFEST" || fail "Manifest 的红果听书播放服务登记对不上"
# 原版签名三件预检（2026-10-31 签名/登录批入树，朋友版随红果整包剥）
[ -f app/src/main/jniLibs/arm64-v8a/libmetasec_ml.so ] || fail "原版签名 .so 找不到"
[ -f app/src/main/assets/hg-metasec/metasec.dex ] || fail "原版签名运行时 dex 找不到"
[ -f app/src/main/java/com/bytedance/covode/number/Covode.java ] || fail "Covode 桩找不到"
# 阅读书架红果组三卡＋带签位 start 锚点预检（2026-10-31 阅读分家批，锚点串定位、不按行号）
grep -qF 'contentBox.addView(ReadingSkin.sectionHeader(this, "红果", null, null));' "$SHELF" || fail "ReadingShelfActivity 的红果组标题行对不上"
grep -qF "contentBox.addView(hgNovelHistoryCard());" "$SHELF" || fail "ReadingShelfActivity 的小说历史卡调用行对不上"
grep -qF "contentBox.addView(hgAudioEntryCard());" "$SHELF" || fail "ReadingShelfActivity 的听书卡调用行对不上"
grep -qF "contentBox.addView(hgAudioListeningCard());" "$SHELF" || fail "ReadingShelfActivity 的在听卡调用行对不上"
grep -qF "private View hgNovelHistoryCard() {" "$SHELF" || fail "ReadingShelfActivity 的小说历史卡方法对不上"
grep -qF "private View hgAudioEntryCard() {" "$SHELF" || fail "ReadingShelfActivity 的听书卡方法对不上"
grep -qF "private View hgAudioListeningCard() {" "$SHELF" || fail "ReadingShelfActivity 的在听卡方法对不上"
grep -qF "public static void start(Context context, String tab) {" "$SHELF" || fail "ReadingShelfActivity 的带签位 start 对不上"
grep -qF 'private static final String EXTRA_TAB = "shelf_tab";' "$SHELF" || fail "ReadingShelfActivity 的 EXTRA_TAB 对不上"
grep -qF "getIntent().getStringExtra(EXTRA_TAB)" "$SHELF" || fail "ReadingShelfActivity 的 onCreate 读签段对不上"

# ---- 本体资源清单（main/mobile 源集，逐份核在再删） ----
RES_FILES="
app/src/main/res/drawable/ic_drama_back.xml
app/src/main/res/drawable/ic_drama_chev.xml
app/src/main/res/drawable/ic_drama_close.xml
app/src/main/res/drawable/ic_drama_comment.xml
app/src/main/res/drawable/ic_drama_exitfull.xml
app/src/main/res/drawable/ic_drama_fullscreen.xml
app/src/main/res/drawable/ic_drama_home.xml
app/src/main/res/drawable/ic_drama_lock.xml
app/src/main/res/drawable/ic_drama_menu.xml
app/src/main/res/drawable/ic_drama_mine.xml
app/src/main/res/drawable/ic_drama_more.xml
app/src/main/res/drawable/ic_drama_pause.xml
app/src/main/res/drawable/ic_drama_play.xml
app/src/main/res/drawable/ic_drama_search.xml
app/src/main/res/drawable/ic_drama_star.xml
app/src/main/res/drawable/ic_drama_theater.xml
app/src/main/res/drawable/bg_hg_badge.xml
app/src/main/res/drawable/bg_hg_book_chip.xml
app/src/main/res/drawable/bg_hg_book_searchbar.xml
app/src/main/res/drawable/bg_hg_cover_r6.xml
app/src/main/res/drawable/bg_hg_cover_r12.xml
app/src/main/res/drawable/bg_hg_author_series_card.xml
app/src/main/res/drawable/ic_hg_author_heart.xml
app/src/main/res/drawable/ic_hg_author_collect.xml
app/src/main/res/drawable/ic_hg_author_comment.xml
app/src/main/res/drawable/bg_hg_follow_btn.xml
app/src/main/res/drawable/ic_hg_search_gray.xml
app/src/main/res/drawable/ic_mine_clock.xml
app/src/main/res/drawable/ic_mine_heart.xml
app/src/main/res/drawable/ic_mine_calendar.xml
app/src/main/res/drawable/ic_mine_bell.xml
app/src/main/res/layout/item_hongguo_search_book.xml
app/src/main/res/anim/hg_push_in.xml
app/src/main/res/anim/hg_pop_out.xml
app/src/main/res/anim/hg_fade_in.xml
app/src/main/res/anim/hg_fade_out.xml
app/src/main/res/anim/hg_hold.xml
app/src/mobile/res/drawable/ic_nav_duanju_outline.xml
app/src/mobile/res/drawable/ic_nav_duanju_filled.xml
"
for f in $RES_FILES; do
  [ -f "$f" ] || fail "红果本体资源找不到：$f"
done

# ---- 接线文件手术（HomeActivity / Manifest / ReadingShelfActivity） ----
python3 - "$HOME" "$MANIFEST" "$SHELF" <<'PYEOF'
import re, sys

def load(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()

def save(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)

def cut_exact(text, block, what):
    if text.count(block) != 1:
        sys.exit("python: 红果剥离锚点对不上（%s，命中 %d 处）" % (what, text.count(block)))
    return text.replace(block, "")

def swap_exact(text, old, new, what):
    if text.count(old) != 1:
        sys.exit("python: 红果剥离锚点对不上（%s，命中 %d 处）" % (what, text.count(old)))
    return text.replace(old, new)

def find_line(lines, needle, what):
    hits = [i for i, ln in enumerate(lines) if needle in ln]
    if len(hits) != 1:
        sys.exit("python: 红果剥离定位对不上（%s，命中 %d 处）" % (what, len(hits)))
    return hits[0]

home_path, manifest_path, shelf_path = sys.argv[1], sys.argv[2], sys.argv[3]

# ---- HomeActivity：import／短剧格注册行／点击分发支整删，条件位改回 false ----
# 注释里带本功能标识（图标名/类名）的两处一并收口成中性文案，免得终检 grep 命中注释残留。
home = load(home_path)
home = swap_exact(home, """        // 短剧格（红果）：只在正式版条件成立时才注册（未注册＝底栏根本没这一格，
        // 不留空位不摆占位）；在设置格之前插入，标签用他亲定的「短剧」
        // （nav_duanju），图标 ic_nav_duanju 双态（规格见 hongguo/duanju-tab-spec.md）。
""", """        // 短剧格（条件格）：条件成立时才在设置格之前注册，不留空位不摆占位。
""", "短剧格注册注释")
home = swap_exact(home, """     * 短剧格的正式版条件位：红果本体（HongguoFeedActivity 与前四阶段全套）
     * 已落地，正式版恒 true 开格；朋友版由 strip-hongguo.sh 在构建前把整套
     * 连同本格注册删净（此方法在朋友版临时检出里被改回 false），不在此留开关。
""", """     * 短剧格的条件位：朋友版整套剥离、此条件恒不成立，格子不注册；
     * 正式版源码里本体在册、恒 true 开格（剥离脚本在本检出里改回 false）。
""", "条件位 javadoc")
home = cut_exact(home, "import com.fongmi.android.tv.hongguo.ui.HongguoFeedActivity;\n", "HomeActivity 红果 import")
home = cut_exact(home, "            tabs.add(new BottomTabs.Spec(R.id.hongguo, R.drawable.ic_nav_duanju_outline, R.drawable.ic_nav_duanju_filled, getString(R.string.nav_duanju), true));\n", "短剧格注册行")
home = cut_exact(home, """        if (itemId == R.id.hongguo) {
            HongguoFeedActivity.start(this);
            overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
            return true;
        }
""", "短剧格点击分发支")
home = cut_exact(home, """        // 红果「启动直达短剧」（抽屉开关，他亲定名）：冷启从桌面进来且开关开着，
        // 直接落红果刷剧页；首页不关、留在任务栈底下，刷剧页按返回就落回首页。
        if (savedInstanceState == null && isHongguoTabEnabled()
                && Intent.ACTION_MAIN.equals(getIntent() == null ? null : getIntent().getAction())
                && com.fongmi.android.tv.hongguo.ui.HgStore.isLaunchDirect()) {
            HongguoFeedActivity.start(this);
            overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
        }
""", "启动直达短剧块")
hlines = home.split("\n")
k = find_line(hlines, "    private boolean isHongguoTabEnabled() {", "isHongguoTabEnabled")
if hlines[k + 1].strip() != "return true;":
    sys.exit("python: isHongguoTabEnabled 方法体形状对不上")
hlines[k + 1] = "        return false;"
save(home_path, "\n".join(hlines))

# ---- Manifest：红果登记注释与十个 Activity＋书城浏览页逐个删（每个必须恰好一处） ----
manifest = load(manifest_path)
manifest = cut_exact(manifest, """        <!-- 红果刷剧页（2026-10-30 从零重建）：竖屏刷剧流，独立一套
             界面与控制，不借 VideoActivity。窗口不转：screenOrientation
             ="behind"＋configChanges 全套自管；横屏观看走独立横屏页
             （重建阶段8 加回登记）。 -->
""", "刷剧页登记注释")
manifest = cut_exact(manifest, """        <!-- 红果短剧新页（2026-10-30 从零重建）：详情/剧场/搜索/作者 -->
""", "短剧新页登记注释")
manifest = cut_exact(manifest, """        <!-- 红果横屏页（2026-10-30 重建阶段8）：真独立页，照官方清单
             userLandscape＋singleTop＋configChanges 全套＋PiP 声明 -->
""", "横屏页登记注释")
manifest = cut_exact(manifest, """        <!-- 红果书城浏览（内置小说/听书源点进去的面） -->
""", "书城浏览页登记注释")
manifest = cut_exact(manifest, """        <!-- 红果听书单独的播放页（分家令：不与普通听书共用播放页） -->
""", "红果听书播放页登记注释")
manifest = cut_exact(manifest, """        <!-- 红果听书专属播放服务（W-15 拆套：与主 PlaybackService 同机制、独立实例） -->
        <service
            android:name="com.fongmi.android.tv.hongguo.book.HgAudioPlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaLibraryService" />
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>
""", "红果听书播放服务登记")
for name in ("HongguoFeedActivity", "HongguoDetailActivity", "HongguoTheaterActivity", "HongguoSearchActivity", "HongguoAuthorActivity", "HongguoPostActivity", "HongguoMineActivity", "HongguoMineEditActivity", "HongguoNovelDetailActivity", "HongguoLandActivity", "HongguoLoginActivity"):
    pattern = r"<activity\s+android:name=\"com\.fongmi\.android\.tv\.hongguo\.ui\." + name + r"\"[^>]*/>"
    manifest, n = re.subn(pattern, "", manifest)
    if n != 1:
        sys.exit("python: Manifest 红果登记对不上（%s，命中 %d 处）" % (name, n))
pattern = r"<activity\s+android:name=\"com\.fongmi\.android\.tv\.hongguo\.book\.HgBookBrowseActivity\"[^>]*/>"
manifest, n = re.subn(pattern, "", manifest)
if n != 1:
    sys.exit("python: Manifest 红果登记对不上（HgBookBrowseActivity，命中 %d 处）" % n)
pattern = r"<activity\s+android:name=\"com\.fongmi\.android\.tv\.hongguo\.book\.HgAudioPlayActivity\"[^>]*/>"
manifest, n = re.subn(pattern, "", manifest)
if n != 1:
    sys.exit("python: Manifest 红果登记对不上（HgAudioPlayActivity，命中 %d 处）" % n)
save(manifest_path, manifest)

# ---- ReadingShelfActivity：红果组三卡＋带签位 start 整块删（锚点串定位） ----
# 删五块：①带签位 start 方法连同 EXTRA_TAB 字段与两段 javadoc（全树唯一调用方
# 是被剥的 HgAudioPlayActivity）；②onCreate 的读签段；③红果组调用块（组注释＋
# 组标题＋三行 addView）；④⑤⑥三个卡方法块连同各自 javadoc（方法体内含对
# tv.hongguo 包的全部引用：两处全限定名＋一处拆拼字符串，随块删净）。
shelf = load(shelf_path)
slines = shelf.split("\n")

def find_lines(lines, needle, what):
    hits = [i for i, ln in enumerate(lines) if needle in ln]
    if len(hits) != 1:
        sys.exit("python: 红果剥离定位对不上（%s，命中 %d 处）" % (what, len(hits)))
    return hits[0]

def cut_span(lines, start, end, what):
    # 删 lines[start..end]（含两端），返回新表
    if start < 0 or end < start or end >= len(lines):
        sys.exit("python: 红果剥离切除范围对不上（%s）" % what)
    return lines[:start] + lines[end + 1:]

def method_block(lines, sig_needle, what):
    # 由方法签名行定位整块：上溯 javadoc（*/ → *… → /**，再含其前空行），
    # 下溯方法结束行（签名后第一行恰为四空格右括号）
    k = find_lines(lines, sig_needle, what)
    if k == 0 or lines[k - 1].strip() != "*/":
        sys.exit("python: 红果剥离方法块形状对不上（%s：签名行上方不是 javadoc 尾）" % what)
    j = k - 1
    while j >= 0 and lines[j].strip() != "/**":
        if not lines[j].lstrip().startswith("*"):
            sys.exit("python: 红果剥离方法块形状对不上（%s：javadoc 体含异物行）" % what)
        j -= 1
    if j < 0:
        sys.exit("python: 红果剥离方法块形状对不上（%s：找不到 javadoc 头）" % what)
    start = j - 1 if j > 0 and lines[j - 1].strip() == "" else j
    end = None
    for m in range(k + 1, len(lines)):
        if lines[m] == "    }":
            end = m
            break
    if end is None:
        sys.exit("python: 红果剥离方法块形状对不上（%s：找不到方法结束行）" % what)
    return start, end

# ③ 红果组调用块：自组注释首行至在听卡 addView 行，四个锚须按序在块内
c0 = find_lines(slines, "// 红果组（他 2026-10-31 亲裁", "红果组调用块注释")
c1 = find_lines(slines, "contentBox.addView(hgAudioListeningCard());", "红果组调用块尾行")
seq = ['sectionHeader(this, "红果", null, null)', "hgNovelHistoryCard()",
       "hgAudioEntryCard()", "hgAudioListeningCard()"]
pos = []
for s in seq:
    hits = [i for i in range(c0, c1 + 1) if s in slines[i]]
    if len(hits) != 1:
        sys.exit("python: 红果组调用块锚点对不上（%s，块内命中 %d 处）" % (s, len(hits)))
    pos.append(hits[0])
if pos != sorted(pos):
    sys.exit("python: 红果组调用块锚点顺序对不上")
slines = cut_span(slines, c0, c1, "红果组调用块")

# ④⑤⑥ 三个卡方法块
for sig, what in (("private View hgNovelHistoryCard() {", "小说历史卡方法块"),
                  ("private View hgAudioEntryCard() {", "听书卡方法块"),
                  ("private View hgAudioListeningCard() {", "在听卡方法块")):
    s0, s1 = method_block(slines, sig, what)
    slines = cut_span(slines, s0, s1, what)

# ① 带签位 start 块：自「落签附加」javadoc（含其前空行）至 start 方法结束行
k = find_lines(slines, "public static void start(Context context, String tab) {", "带签位 start")
f = find_lines(slines, 'private static final String EXTRA_TAB = "shelf_tab";', "EXTRA_TAB 字段")
if f > k:
    sys.exit("python: 带签位 start 块形状对不上（EXTRA_TAB 不在 start 之前）")
if slines[f - 1].strip() != "/** 落签附加（start 带签位版专用）：新建实例时落到指定签。 */":
    sys.exit("python: 带签位 start 块形状对不上（EXTRA_TAB 的 javadoc 对不上）")
start = f - 2 if f >= 2 and slines[f - 2].strip() == "" else f - 1
end = None
for m in range(k + 1, len(slines)):
    if slines[m] == "    }":
        end = m
        break
if end is None:
    sys.exit("python: 带签位 start 块形状对不上（找不到方法结束行）")
slines = cut_span(slines, start, end, "带签位 start 块")
shelf = "\n".join(slines)

# ② onCreate 读签段整删
shelf = cut_exact(shelf, """        // 带签位新建（回书架专用口）：无重建存档时按附加落签
        if (savedInstanceState == null) {
            String tab = getIntent().getStringExtra(EXTRA_TAB);
            if ("NOVEL".equals(tab) || "COMIC".equals(tab) || "AUDIO".equals(tab)) currentTab = tab;
        }
""", "onCreate 读签段")

# 剥后自检：红果包引用与三卡痕迹零残留、通用口完好
for bad in ("hongguo", "hgNovelHistoryCard", "hgAudioEntryCard",
            "hgAudioListeningCard", "EXTRA_TAB", "\"红果\""):
    if bad in shelf:
        sys.exit("python: ReadingShelfActivity 剥后仍有残留：%s" % bad)
if shelf.count("public static void start(Context context) {") != 1:
    sys.exit("python: ReadingShelfActivity 剥后通用 start 不完好")
if "private View sourceCard(boolean novelTab) {" not in shelf:
    sys.exit("python: ReadingShelfActivity 剥后 sourceCard 不见了")
save(shelf_path, shelf)
PYEOF

# ---- Nano：红果 process 的 import 与注册行删 ----
sed -i "/import com\.fongmi\.android\.tv\.server\.process\.HongguoStream;/d" "$NANO"
sed -i "/process\.add(new HongguoStream());/d" "$NANO"

# ---- 短剧格字符串删 ----
sed -i "/<string name=\"nav_duanju\">/d" "$MSTRINGS"

# ---- 本体文件整删 ----
rm -rf "$HGPKG" "$HGTEST"
rm "$STREAM"
# 原版签名三件整删（朋友版不带 metasec .so/运行时 dex/Covode 桩）
rm app/src/main/jniLibs/arm64-v8a/libmetasec_ml.so
rm -rf app/src/main/assets/hg-metasec
rm -rf app/src/main/java/com/bytedance
rm -rf app/src/mobile/java/com/fongmi/android/tv/hongguo/book
rm -rf app/src/mobile/java/com/fongmi/android/tv/hongguo/ui
for f in $RES_FILES; do
  rm "$f"
done

# ---- 复查一：本功能专属标识全树零残留（共用件的泛 Hongguo 字样不在此列，见文件头白名单） ----
if grep -rn -E "com\.fongmi\.android\.tv\.hongguo|HongguoFeedActivity|HongguoDetailActivity|HongguoTheaterActivity|HongguoSearchActivity|HongguoAuthorActivity|HongguoMineActivity|HongguoLandActivity|HongguoNovelDetailActivity|HongguoStream|ic_nav_duanju|nav_duanju|ic_drama_" app/src/main app/src/mobile app/src/leanback app/src/test --include=*.java --include=*.xml; then
  fail "删完后仍有红果本功能残留"
fi
[ ! -e app/src/main/jniLibs/arm64-v8a/libmetasec_ml.so ] || fail "删完后原版签名 .so 仍在"
[ ! -e app/src/main/assets/hg-metasec ] || fail "删完后原版签名 dex 目录仍在"
[ ! -e app/src/main/java/com/bytedance ] || fail "删完后 Covode 桩目录仍在"

# ---- 复查二：共用件与框架必须完好 ----
for f in app/src/main/java/com/fongmi/android/tv/api/FqCommentSigner.java \
         app/src/main/java/com/fongmi/android/tv/api/HongguoCommentApi.java \
         app/src/main/java/com/fongmi/android/tv/api/HongguoDanmakuApi.java; do
  [ -f "$f" ] || fail "剥完后共用件不见了：$f"
done
grep -qF "register(new Qr115Provider());" "$LM" || fail "剥完后 115 提供方注册不见了"
grep -qF "register(new KuaishouLoginProvider());" "$LM" || fail "剥完后快手提供方注册不见了"
grep -qF "register(new GoogleLoginProvider());" "$LM" || fail "剥完后谷歌提供方注册不见了"
# 注：QQ 提供方注册不在此断言——朋友版自 2026-10-26 起由先行的 strip-friend-seed
# 剥 QQ 音乐登录（其注册行已删是预期形态，完整性由 friend-seed 自己的复查负责）。
grep -qF "public static boolean requestLogin(String providerId)" "$LM" || fail "剥完后 requestLogin 通用分发不见了"
grep -qF "public static String getCookie(String providerId)" "$LM" || fail "剥完后 getCookie 通用口不见了"
grep -qF "process.add(new Proxy());" "$NANO" || fail "剥完后 Nano 的 Proxy process 不见了"
grep -qF "process.add(new Local());" "$NANO" || fail "剥完后 Nano 的 Local process 不见了"
grep -qF "BottomTabs.Spec(R.id.vod" "$HOME" || fail "剥完后底栏点播格注册不见了"
grep -qF "BottomTabs.Spec(R.id.setting" "$HOME" || fail "剥完后底栏设置格注册不见了"
grep -qF ".ui.activity.VideoActivity" "$MANIFEST" || fail "剥完后 Manifest 的 VideoActivity 登记不见了"
grep -qF "public static void start(Context context) {" "$SHELF" || fail "剥完后 ReadingShelfActivity 的通用 start 不见了"
grep -qF "private View sourceCard(boolean novelTab) {" "$SHELF" || fail "剥完后 ReadingShelfActivity 的 sourceCard 不见了"
if grep -q "hongguo" "$SHELF"; then
  fail "剥完后 ReadingShelfActivity 仍有红果包引用残留"
fi
echo "朋友版：第三方红果已删除（本体包＋mobile 两目录＋代理解密 process＋底栏短剧格＋Manifest 登记＋本体资源＋阅读书架红果组三卡，规则年代共用件保留）"
