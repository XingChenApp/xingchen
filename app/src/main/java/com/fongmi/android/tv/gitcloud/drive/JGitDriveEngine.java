package com.fongmi.android.tv.gitcloud.drive;

import android.text.TextUtils;

import com.fongmi.android.tv.gitcloud.GitCloudException;
import com.fongmi.android.tv.gitcloud.GitProviderType;
import com.github.catvod.utils.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JGitDriveEngine implements GitDriveEngine {

    /**
     * 在途操作的取消监视器集合。旧实现 cancel() 只往一张没人读的 map 里 put：
     * 任务停不下来、条目还只增不减。现在取消直接落到 JGit 的 ProgressMonitor 上
     * （传输层会轮询 isCancelled 并中止），操作结束在 finally 里注销，不留存量。
     */
    private final Set<CancelMonitor> active = ConcurrentHashMap.newKeySet();

    private static final class CancelMonitor implements ProgressMonitor {

        private volatile boolean cancelled;

        void trip() {
            cancelled = true;
        }

        @Override
        public void start(int totalTasks) {
        }

        @Override
        public void beginTask(String title, int totalWork) {
        }

        @Override
        public void update(int completed) {
        }

        @Override
        public void endTask() {
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        // ProgressMonitor 在新版 JGit 多了这个抽象方法，老版没有；
        // 不带 @Override 写一份，两版都能编过。
        public void showDuration(boolean enabled) {
        }
    }

    @Override
    public GitDriveState bind(GitDriveConfig config) throws GitCloudException {
        require(config);
        if (new File(config.worktreeDir, ".git").exists()) return status(config);
        File tmp = new File(config.worktreeDir.getParentFile(), config.worktreeDir.getName() + ".tmp");
        Path.clear(tmp);
        CancelMonitor monitor = new CancelMonitor();
        active.add(monitor);
        try {
            org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
                    .setURI(config.repo.cloneUrl)
                    .setDirectory(tmp)
                    .setProgressMonitor(monitor)
                    .setCredentialsProvider(credentials(config));
            if (!TextUtils.isEmpty(config.branch())) command.setBranch(config.branch());
            command.call().close();
            if (monitor.isCancelled()) throw new GitCloudException("绑定已取消");
            Path.clear(config.worktreeDir);
            if (!tmp.renameTo(config.worktreeDir)) throw new GitCloudException("切换本地工作目录失败");
            return status(config);
        } catch (GitCloudException e) {
            throw e;
        } catch (Throwable e) {
            Path.clear(tmp);
            if (monitor.isCancelled()) throw new GitCloudException("绑定已取消", e);
            throw new GitCloudException("绑定仓库失败：" + sanitize(e.getMessage()), e);
        } finally {
            active.remove(monitor);
        }
    }

    @Override
    public GitDriveState pull(GitDriveConfig config) throws GitCloudException {
        require(config);
        CancelMonitor monitor = new CancelMonitor();
        active.add(monitor);
        try (Git git = Git.open(config.worktreeDir)) {
            git.pull().setProgressMonitor(monitor).setCredentialsProvider(credentials(config)).call();
            if (monitor.isCancelled()) throw new GitCloudException("同步已取消");
            return state(git, "同步完成");
        } catch (GitCloudException e) {
            throw e;
        } catch (Throwable e) {
            if (monitor.isCancelled()) throw new GitCloudException("同步已取消", e);
            throw new GitCloudException("同步失败：" + sanitize(e.getMessage()), e);
        } finally {
            active.remove(monitor);
        }
    }

    @Override
    public CommitResult commitAndPush(GitDriveConfig config, List<FileChange> changes) throws GitCloudException {
        require(config);
        if (changes == null || changes.isEmpty()) throw new GitCloudException("没有需要上传的文件");
        CancelMonitor monitor = new CancelMonitor();
        active.add(monitor);
        try (Git git = openOrBind(config)) {
            for (FileChange change : changes) {
                if (monitor.isCancelled()) throw new GitCloudException("上传已取消");
                if (change == null || TextUtils.isEmpty(change.path)) continue;
                File target = safeFile(config.worktreeDir, change.path);
                if (change.delete) {
                    Path.clear(target);
                    git.rm().addFilepattern(change.path).call();
                } else {
                    write(target, change.data);
                    git.add().addFilepattern(change.path).call();
                }
            }
            Status status = git.status().call();
            if (status.isClean()) {
                // 工作区干净不等于已同步：上次 commit 成功、push 失败时本地提交留盘，
                // 此后同内容再调会被这里判「无变化」直接返回，那批提交永远补推不出去。
                // 先查本地分支相对远端跟踪分支是否 ahead，有未推送提交先补推再报无变化。
                if (hasUnpushedCommits(git)) {
                    git.push().setProgressMonitor(monitor).setCredentialsProvider(credentials(config)).call();
                    if (monitor.isCancelled()) throw new GitCloudException("上传已取消");
                    CommitResult result = new CommitResult();
                    ObjectId head = git.getRepository().resolve("HEAD");
                    result.commitSha = head == null ? "" : head.name();
                    result.pushed = true;
                    result.message = "已补推此前未推送的提交";
                    return result;
                }
                CommitResult result = new CommitResult();
                result.message = "没有文件变化";
                return result;
            }
            String message = "upload: WebHTV files " + System.currentTimeMillis();
            ObjectId id = git.commit().setMessage(message).setAuthor("WebHTV", "webhtv@app.local").call().getId();
            git.push().setProgressMonitor(monitor).setCredentialsProvider(credentials(config)).call();
            if (monitor.isCancelled()) throw new GitCloudException("上传已取消");
            CommitResult result = new CommitResult();
            result.commitSha = id == null ? "" : id.name();
            result.pushed = true;
            result.message = message;
            return result;
        } catch (GitCloudException e) {
            throw e;
        } catch (Throwable e) {
            if (monitor.isCancelled()) throw new GitCloudException("上传已取消", e);
            throw new GitCloudException("上传失败：" + sanitize(e.getMessage()), e);
        } finally {
            active.remove(monitor);
        }
    }

    @Override
    public GitDriveState status(GitDriveConfig config) throws GitCloudException {
        require(config);
        try (Git git = Git.open(config.worktreeDir)) {
            return state(git, "本地缓存就绪");
        } catch (Throwable e) {
            throw new GitCloudException("读取仓库状态失败：" + sanitize(e.getMessage()), e);
        }
    }

    @Override
    public void cancel(String taskId) {
        // 引擎的操作不带 taskId 入参，在途操作就登记在 active 里：
        // 取消直接触发全部在途监视器（JGit 传输轮询到即中止，本地段在检查点停）。
        for (CancelMonitor monitor : active) monitor.trip();
    }

    /** 本地分支是否有未推送到远端的提交（跟踪分支口径；跟踪信息缺失时退到与 origin/<分支> 的引用直比，引用也没有＝远端从未同步过，有 HEAD 即未推送） */
    private boolean hasUnpushedCommits(Git git) throws Exception {
        org.eclipse.jgit.lib.Repository repo = git.getRepository();
        ObjectId head = repo.resolve("HEAD");
        if (head == null) return false;
        // 分支取仓库当前检出分支（push 推的正是它），不依赖 config 里的 repo 描述
        String branch = repo.getBranch();
        if (branch == null) return false;
        org.eclipse.jgit.lib.BranchTrackingStatus tracking = org.eclipse.jgit.lib.BranchTrackingStatus.of(repo, branch);
        if (tracking != null) return tracking.getAheadCount() > 0;
        ObjectId remoteHead = repo.resolve("refs/remotes/origin/" + branch);
        return remoteHead == null || !remoteHead.equals(head);
    }

    private Git openOrBind(GitDriveConfig config) throws Exception {
        if (!new File(config.worktreeDir, ".git").exists()) bind(config);
        return Git.open(config.worktreeDir);
    }

    private GitDriveState state(Git git, String message) throws Exception {
        GitDriveState state = new GitDriveState();
        state.branch = git.getRepository().getBranch();
        ObjectId head = git.getRepository().resolve("HEAD");
        state.head = head == null ? "" : head.name();
        state.clean = git.status().call().isClean();
        state.message = message;
        return state;
    }

    private UsernamePasswordCredentialsProvider credentials(GitDriveConfig config) {
        String username = config.account.providerType == GitProviderType.CNB ? "cnb" : config.account.username;
        if (TextUtils.isEmpty(username)) username = "x-access-token";
        return new UsernamePasswordCredentialsProvider(username, config.token);
    }

    private void require(GitDriveConfig config) throws GitCloudException {
        if (config == null || config.account == null || config.repo == null) throw new GitCloudException("同步配置不完整");
        if (TextUtils.isEmpty(config.repo.cloneUrl)) throw new GitCloudException("仓库缺少 HTTPS 地址");
        if (TextUtils.isEmpty(config.token)) throw new GitCloudException("token 为空");
        if (config.worktreeDir == null) throw new GitCloudException("本地工作目录为空");
        File parent = config.worktreeDir.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new GitCloudException("创建本地工作目录失败");
    }

    private File safeFile(File root, String path) throws GitCloudException {
        try {
            File file = new File(root, path.replace('\\', '/')).getCanonicalFile();
            File canonicalRoot = root.getCanonicalFile();
            if (!file.getPath().startsWith(canonicalRoot.getPath())) throw new GitCloudException("文件路径不安全");
            return file;
        } catch (GitCloudException e) {
            throw e;
        } catch (Throwable e) {
            throw new GitCloudException("文件路径不合法", e);
        }
    }

    private void write(File file, byte[] data) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new GitCloudException("创建目录失败");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data == null ? new byte[0] : data);
        }
    }

    private String sanitize(String message) {
        if (message == null) return "";
        return message.replaceAll("(?i)(token|authorization|password)=?[^\\s]+", "$1=***");
    }
}
