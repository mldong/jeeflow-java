package com.mldong.jeeflow.spi;

/**
 * 用户信息提供者 SPI
 *
 * @author mldong
 */
public interface IUserProvider {

    /** 获取用户姓名 */
    String getRealName(String userId);

    /** 获取部门 ID */
    String getDeptId(String userId);

    /** 获取部门名称 */
    String getDeptName(String userId);

    /** 获取岗位 ID */
    String getPostId(String userId);

    /** 获取岗位名称 */
    String getPostName(String userId);
}
