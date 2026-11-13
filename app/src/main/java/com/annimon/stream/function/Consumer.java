package com.annimon.stream.function;

/**
 * 8.83 所带 annimon-stream 库的 {@code Consumer} 接口同包名补齐
 * （官包内多处本体类签名引用，如 ThreadTool.async、CustomBottomRecyclerViewPopup
 * 的 getOnCreateCallback；本树未引入 annimon 库，按原签名补此单法接口）。
 */
@FunctionalInterface
public interface Consumer<T> {
    void accept(T t);
}
