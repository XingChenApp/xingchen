package com.fongmi.android.tv.ui.adapter;
import com.fongmi.android.tv.utils.ToastUtil;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Plugin;
import com.fongmi.android.tv.bean.PluginHealth;
import com.fongmi.android.tv.bean.PluginKinds;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.ShortDramaHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.ViewHolder> {
    private final List<Plugin> plugins;
    private final Runnable onChanged;

    public PluginAdapter(List<Plugin> plugins, Runnable onChanged) {
        this.plugins = plugins;
        this.onChanged = onChanged;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Plugin p = plugins.get(position);
        holder.tvName.setText(p.getName());
        holder.tvVersion.setText(p.isEnabled() ? "已启用" : "已停用");
        bindHealth(holder, p);
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(p.isSelected());
        holder.switchEnable.setOnCheckedChangeListener(null);
        holder.switchEnable.setChecked(p.isEnabled());
        
        // 短剧标记：行内「短剧」小标是可见入口（点它切换），长按整行保留为备用入口。
        // HTML 源不带此标（短剧标记是 PY/JS 蜘蛛源的概念，ShortDramaHelper 同口径）：
        // 只隐不删位——INVISIBLE 保住行高与开关/删除钮的位置，右上留白；
        // 点击/长按两个切换口一并摘除，防看不见的标记被误改。
        String fileName = new File(p.getPath() == null ? "" : p.getPath()).getName();
        if (PluginKinds.HTML.equals(p.getType())) {
            holder.tvShortDrama.setVisibility(View.INVISIBLE);
            holder.tvShortDrama.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(null);
        } else {
            holder.tvShortDrama.setVisibility(View.VISIBLE);
            updateShortDramaBadge(holder.tvShortDrama, fileName);
            holder.tvShortDrama.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= plugins.size()) return;
                String fn = new File(plugins.get(pos).getPath() == null ? "" : plugins.get(pos).getPath()).getName();
                boolean next = !ShortDramaHelper.isPluginShortDrama(fn);
                ShortDramaHelper.setPluginShortDrama(fn, next);
                updateShortDramaBadge(holder.tvShortDrama, fn);
                ToastUtil.show(v.getContext(), next ? "已标记为短剧源" : "已取消短剧标记", Toast.LENGTH_SHORT);
            });
            holder.itemView.setOnLongClickListener(v -> {
                boolean next = !ShortDramaHelper.isPluginShortDrama(fileName);
                ShortDramaHelper.setPluginShortDrama(fileName, next);
                updateShortDramaBadge(holder.tvShortDrama, fileName);
                ToastUtil.show(v.getContext(), next ? "已标记为短剧源" : "已取消短剧标记", Toast.LENGTH_SHORT);
                return true;
            });
        }

        holder.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
            p.setSelected(checked);
        });
        
        holder.switchEnable.setOnCheckedChangeListener((btn, checked) -> {
            p.setEnabled(checked);
            holder.tvVersion.setText(checked ? "已启用" : "已停用");
        });
        
        holder.tvDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= plugins.size()) return;
            Plugin item = plugins.get(pos);
            String itemPath = item.getPath();
            boolean py = itemPath != null && itemPath.endsWith(".py");
            boolean js = itemPath != null && (itemPath.endsWith(".js") || itemPath.endsWith(".wv"));
            if (itemPath != null) new File(itemPath).delete();
            plugins.remove(pos);
            notifyItemRemoved(pos);
            // 单删也要走失效：只清该插件对应的蜘蛛＋首页缓存并让首页立刻重载，
            // 否则首页还挂着已删插件的旧内容；旧写法是单删一个就全清全部 py 蜘蛛。
            // html 无蜘蛛：不进这两支（旧二分会把删 html 误当 js 全清），靠下面的
            // RefreshEvent.home 让各枚举面按新目录状态摘掉该源。
            try {
                if (py) BaseLoader.get().invalidatePyScript(itemPath);
                else if (js) BaseLoader.get().clearJs();
            } catch (Throwable ignored) {}
            RefreshEvent.home();
            if (onChanged != null) onChanged.run();
        });
    }

    private void updateShortDramaBadge(TextView badge, String fileName) {
        boolean marked = ShortDramaHelper.isPluginShortDrama(fileName);
        badge.setTextColor(marked ? 0xFFF0A400 : 0xFF8A8F98);
    }

    @Override
    public int getItemCount() {
        return plugins.size();
    }

    public void selectAll(boolean checked) {
        for (Plugin p : plugins) p.setSelected(checked);
        notifyDataSetChanged();
    }

    public List<Plugin> getSelected() {
        List<Plugin> result = new ArrayList<>();
        for (Plugin p : plugins) if (p.isSelected()) result.add(p);
        return result;
    }

    /** 健康检测结果：插件名下直接出档位（持久化的上次结果；检测中由 Activity 标记）。 */
    private void bindHealth(ViewHolder holder, Plugin p) {
        android.content.Context c = holder.itemView.getContext();
        int grade;
        long ms = 0;
        String reason = "";
        if (testingPaths.contains(p.getPath())) {
            grade = PluginHealth.TESTING;
        } else {
            PluginHealth.Result r = PluginHealth.get(c, p.getPath());
            grade = r.grade;
            ms = r.ms;
            reason = r.reason;
        }
        if (grade == PluginHealth.UNKNOWN) {
            holder.tvHealth.setVisibility(View.GONE);
            return;
        }
        holder.tvHealth.setVisibility(View.VISIBLE);
        String label = PluginHealth.label(grade);
        if (grade == PluginHealth.OK || grade == PluginHealth.SLOW) {
            label += " · " + String.format(java.util.Locale.US, "%.1fs", ms / 1000f);
        } else if (grade == PluginHealth.DEAD && !reason.isEmpty()) {
            label += " · " + reason;
        }
        holder.tvHealth.setText(label);
        holder.tvHealth.setTextColor(PluginHealth.color(grade));
    }

    /** 检测中集合（Activity 维护，Adapter 只读）。 */
    private final java.util.Set<String> testingPaths = new java.util.HashSet<>();

    public void setTesting(java.util.Set<String> paths) {
        testingPaths.clear();
        if (paths != null) testingPaths.addAll(paths);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvName, tvVersion, tvDelete, tvShortDrama, tvHealth;
        Switch switchEnable;
        ViewHolder(View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            tvName = itemView.findViewById(R.id.tv_name);
            tvVersion = itemView.findViewById(R.id.tv_version);
            tvDelete = itemView.findViewById(R.id.tv_delete);
            tvShortDrama = itemView.findViewById(R.id.tv_short_drama);
            tvHealth = itemView.findViewById(R.id.tv_health);
            switchEnable = itemView.findViewById(R.id.switch_enable);
        }
    }
}