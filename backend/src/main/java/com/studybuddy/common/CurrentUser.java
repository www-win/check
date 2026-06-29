package com.studybuddy.common;

/** 基于 ThreadLocal 的当前登录用户上下文。由 AuthInterceptor 写入、清理。 */
public final class CurrentUser {
    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    /** 取当前用户 id；未登录抛 40100。 */
    public static Long get() {
        Long userId = HOLDER.get();
        if (userId == null) {
            throw new BizException(40100, "未登录");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
