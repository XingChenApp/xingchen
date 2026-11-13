package com.example.hikerview.ui.setting;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.model.ViewCollection;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.StringUtil;

import java.util.List;

/**
 * 官 {@code com.example.hikerview.ui.setting.CollectionListAdapter} 的插件面移植。
 *
 * <p>官本体适配器强耦合官 CollectionListActivity 与 ViewCollection 模型及
 * BGABadge/SpinKit 三方视图，星辰无此栈，本体绑定逻辑未落（聚阅 libs 不调
 * hikerPop.collectBottom，此类在插件里只被 newCollecListAdapter 用作
 * 嵌套类型来源）。本类落插件 JS 实用的全部嵌套面：
 * RuleHolder/GridHolder（构造器 findViewById 集合与 getter 名照官 jadx，
 * 布局 item_history_pic/item_history_pic_vertical 已按官重建）、
 * MyDividerItem、OnItemClickListener。
 *
 * <p>记名差异：①官 holder 为 private static，星辰放 public static
 * （JS 反射构造需要）；②item_badge（官 BGABadgeRelativeLayout）与
 * item_update_loading_view（官 SpinKitView）星辰无此二库，字段以 View 落；
 * ③MyDividerItem 官只有 (Context) 构造，插件 JS 以无参构造调用，
 * 星辰补无参构造（context 为空时 getLeftRight 回 0）；
 * ④getLeftRight 的基类官为 ui.view.BaseDividerItem（未移植），
 * 星辰直承 RecyclerView.ItemDecoration；
 * ⑤isWebViewGroup/setWebViewGroup 已照官落（官字段/两法仅布尔存取）；
 * ⑥getItemViewType 已由①团解锁落码（官 ViewCollection 模型已转写在位，
 * list 元素照官强转取 getPicUrl；本类 list 仍为 List<?> 插件面签名、记名）。
 */
public class CollectionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(View view, int position);

        void onLongClick(View view, int position);

        void onVideoPlay(View view, int position);
    }

    private final Context context;
    private final List<?> list;
    private boolean isWebViewGroup;
    private boolean useGrid;
    private OnItemClickListener onItemClickListener;

    /** 官三型常量（CollectionListAdapter.smali 构造 .line 47-49）：noPicType=1、picType=2、gridType=3。 */
    private final int noPicType = 1;
    private final int picType = 2;
    private final int gridType = 3;

    public CollectionListAdapter(Context context, List<?> list) {
        this.context = context;
        this.list = list;
    }

    public final boolean isWebViewGroup() {
        return this.isWebViewGroup;
    }

    public final void setWebViewGroup(boolean z) {
        this.isWebViewGroup = z;
    }

    public final boolean getUseGrid() {
        return this.useGrid;
    }

    public final void setUseGrid(boolean z) {
        this.useGrid = z;
    }

    public final void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public final MyDividerItem getDividerItem() {
        return new MyDividerItem(this.context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == 3) {
            return new GridHolder(LayoutInflater.from(this.context)
                    .inflate(R.layout.item_history_pic_vertical, parent, false));
        }
        return new RuleHolder(LayoutInflater.from(this.context)
                .inflate(R.layout.item_history_pic, parent, false));
    }

    /**
     * 官 getItemViewType（CollectionListAdapter.smali .line 95-101）逐字转写（①团解锁，
     * 官 ViewCollection 模型已落位）：非网页组且 useGrid 回 gridType；picUrl 非空且
     * 以 "http" 开头回 picType；其余回 noPicType。list 为空的失败形态照官（checkNotNull 后 NPE）。
     */
    @Override
    public int getItemViewType(int position) {
        String picUrl = ((ViewCollection) this.list.get(position)).getPicUrl();
        if (!this.isWebViewGroup && this.useGrid) {
            return this.gridType;
        }
        if (!StringUtil.isEmpty(picUrl) && picUrl.startsWith("http")) {
            return this.picType;
        }
        return this.noPicType;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        // 官绑定逻辑读 ViewCollection 模型字段，星辰无此模型，未落（见类注）。
    }

    @Override
    public int getItemCount() {
        return this.list == null ? 0 : this.list.size();
    }

    public static class RuleHolder extends RecyclerView.ViewHolder {
        private View item_ad_bg;
        private View item_badge;
        private TextView item_history;
        private View item_result_img_bg;
        private ImageView item_reult_img;
        private TextView item_status;
        private View item_update_loading_view;
        private TextView item_url;
        private TextView item_video;
        private View item_video_card;
        private TextView title;
        private View title_bg;

        public RuleHolder(View view) {
            super(view);
            this.item_badge = view.findViewById(R.id.item_bg);
            this.title = view.findViewById(R.id.item_ad_title);
            this.item_result_img_bg = view.findViewById(R.id.item_result_img_bg);
            this.item_reult_img = view.findViewById(R.id.item_reult_img);
            this.title_bg = view.findViewById(R.id.title_bg);
            this.item_url = view.findViewById(R.id.item_ad_url);
            this.item_history = view.findViewById(R.id.item_history);
            this.item_status = view.findViewById(R.id.item_status);
            this.item_video_card = view.findViewById(R.id.item_video_card);
            this.item_ad_bg = view.findViewById(R.id.item_ad_bg);
            this.item_video = view.findViewById(R.id.item_video);
            this.item_update_loading_view = view.findViewById(R.id.spin_kit);
        }

        public final View getItem_badge() {
            return this.item_badge;
        }

        public final TextView getTitle() {
            return this.title;
        }

        public final View getItem_result_img_bg() {
            return this.item_result_img_bg;
        }

        public final ImageView getItem_reult_img() {
            return this.item_reult_img;
        }

        public final View getTitle_bg() {
            return this.title_bg;
        }

        public final TextView getItem_url() {
            return this.item_url;
        }

        public final TextView getItem_history() {
            return this.item_history;
        }

        public final TextView getItem_status() {
            return this.item_status;
        }

        public final View getItem_video_card() {
            return this.item_video_card;
        }

        public final View getItem_ad_bg() {
            return this.item_ad_bg;
        }

        public final TextView getItem_video() {
            return this.item_video;
        }

        public final View getItem_update_loading_view() {
            return this.item_update_loading_view;
        }
    }

    public static class GridHolder extends RecyclerView.ViewHolder {
        private TextView iconText;
        private View item_badge;
        private TextView item_history;
        private CardView item_result_img_bg;
        private ImageView item_reult_img;
        private View item_update_loading_view;
        private TextView title;

        public GridHolder(View view) {
            super(view);
            this.item_badge = view.findViewById(R.id.item_bg);
            this.item_result_img_bg = view.findViewById(R.id.item_result_img_bg);
            this.title = view.findViewById(R.id.item_ad_title);
            this.item_reult_img = view.findViewById(R.id.item_reult_img);
            this.item_history = view.findViewById(R.id.item_history);
            this.item_update_loading_view = view.findViewById(R.id.spin_kit);
            this.iconText = view.findViewById(R.id.icon_text);
        }

        public final View getItem_badge() {
            return this.item_badge;
        }

        public final TextView getTitle() {
            return this.title;
        }

        public final TextView getItem_history() {
            return this.item_history;
        }

        public final ImageView getItem_reult_img() {
            return this.item_reult_img;
        }

        public final View getItem_update_loading_view() {
            return this.item_update_loading_view;
        }

        public final CardView getItem_result_img_bg() {
            return this.item_result_img_bg;
        }

        public final TextView getIconText() {
            return this.iconText;
        }
    }

    public static class MyDividerItem extends RecyclerView.ItemDecoration {
        private Context context;

        public MyDividerItem() {
        }

        public MyDividerItem(Context context) {
            this.context = context;
        }

        public final Context getContext() {
            return this.context;
        }

        public final void setContext(Context context) {
            this.context = context;
        }

        public int getLeftRight(int itemViewType) {
            if (this.context == null) {
                return 0;
            }
            return DisplayUtil.dpToPx(this.context, itemViewType == 3 ? 10 : 0);
        }
    }
}
