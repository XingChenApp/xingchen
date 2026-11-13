package com.example.hikerview.model;

/**
 * 官 {@code com.example.hikerview.model.BaseResultItem} 的签名占位类型。
 *
 * <p>存在理由只有一个：官 {@code DetailUIHelper.dealUrlSimply} 的签名第四参是
 * 本类型，插件（hikerPop.js 的 simpleRoute）以该签名直调、且恒传 null。
 * 星辰内部的条目模型是 {@code com.fongmi.android.tv.api.hk.HkItem}，由
 * {@code ui.hk.HkDetailBridge} 消费；本类不承载字段与行为，适配层收到
 * 非 null 值也无法映射（插件面无此用法），一律按 null 条目转交。
 */
public class BaseResultItem {
}
