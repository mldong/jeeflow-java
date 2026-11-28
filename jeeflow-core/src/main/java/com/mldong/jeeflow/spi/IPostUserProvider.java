package com.mldong.jeeflow.spi;

import java.util.List;

/** 岗位成员查询 SPI（可选，issues/259）。 */
public interface IPostUserProvider {

    /** 按岗位编码取用户 ID 列表；岗位不存在或无成员时返回空列表。 */
    List<String> findByPost(String postCode);
}
