package com.mldong.jeeflow.spi;

/**
 * 用户信息提供者 SPI —— 一次查询返回完整用户信息
 *
 * <p>业务方只需实现 {@link #getUser(String)} 一次查库/调 RPC，
 * 引擎内部通过 {@link UserInfo} 取各字段，避免 5 次 IO。</p>
 *
 * @author mldong
 */
public interface IUserProvider {

    /** 一次返回用户全部信息（为空时返回 null） */
    UserInfo getUser(String userId);

    /** 用户信息 DTO */
    class UserInfo {
        private String userId;
        private String realName;
        private String deptId;
        private String deptName;
        private String postId;
        private String postName;

        public static UserInfo of(String userId) {
            UserInfo u = new UserInfo();
            u.userId = userId;
            u.realName = userId;
            return u;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getDeptId() { return deptId; }
        public void setDeptId(String deptId) { this.deptId = deptId; }
        public String getDeptName() { return deptName; }
        public void setDeptName(String deptName) { this.deptName = deptName; }
        public String getPostId() { return postId; }
        public void setPostId(String postId) { this.postId = postId; }
        public String getPostName() { return postName; }
        public void setPostName(String postName) { this.postName = postName; }
    }
}
