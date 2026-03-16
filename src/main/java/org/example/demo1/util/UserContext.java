package org.example.demo1.util;

/**
 * 用户上下文，使用 ThreadLocal 存储当前登录用户 ID 和角色
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_ROLE_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void setUserRole(Integer role) {
        USER_ROLE_HOLDER.set(role);
    }

    public static Integer getUserRole() {
        return USER_ROLE_HOLDER.get();
    }

    /** 判断当前用户是否为管理员（role == 1） */
    public static boolean isAdmin() {
        Integer role = USER_ROLE_HOLDER.get();
        return role != null && role == 1;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        USER_ROLE_HOLDER.remove();
    }
}
