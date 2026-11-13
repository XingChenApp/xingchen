package com.example.hikerview.constants;

import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/constants/JSONPreFilter.smali，全类照落）。
 * filter0 的排除集 13 项与三工厂的增减项逐项照官。
 */
public class JSONPreFilter {

    private static SimplePropertyPreFilter filter0(SimplePropertyPreFilter filter) {
        filter.getExcludes().add("associatedModelsMapForJoinTable");
        filter.getExcludes().add("associatedModelsMapWithFK");
        filter.getExcludes().add("associatedModelsMapWithoutFK");
        filter.getExcludes().add("fieldsToSetToDefault");
        filter.getExcludes().add("listToClearAssociatedFK");
        filter.getExcludes().add("listToClearSelfFK");
        filter.getExcludes().add("saved");
        filter.getExcludes().add("order");
        filter.getExcludes().add("id");
        filter.getExcludes().add("gmtCreate");
        filter.getExcludes().add("gmtModified");
        filter.getExcludes().add("lastUseTime");
        filter.getExcludes().add("displayName");
        return filter;
    }

    public static SimplePropertyPreFilter getShareFilter() {
        SimplePropertyPreFilter filter = filter0(new SimplePropertyPreFilter(new String[0]));
        filter.getExcludes().add("storage");
        filter.getExcludes().add("pageList");
        return filter;
    }

    public static SimplePropertyPreFilter getShareFilter2(boolean z) {
        SimplePropertyPreFilter filter = filter0(new SimplePropertyPreFilter(new String[0]));
        filter.getExcludes().remove("gmtModified");
        filter.getExcludes().add("pageList");
        if (z) {
            filter.getIncludes().add("title");
            filter.getIncludes().add("time");
            filter.getIncludes().add("mTitle");
            filter.getIncludes().add("version");
            filter.getIncludes().add("gmtModified");
        }
        return filter;
    }

    public static SimplePropertyPreFilter getSimpleFilter() {
        SimplePropertyPreFilter filter = filter0(new SimplePropertyPreFilter(new String[0]));
        filter.getExcludes().add("storage");
        return filter;
    }
}
