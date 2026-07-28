-- jeeflow 工作流表结构（完全兼容 mldong-boot2）
-- H2 版本（MySQL 兼容模式）

CREATE TABLE IF NOT EXISTS wf_process_define (
    id            BIGINT        NOT NULL COMMENT '主键',
    name          VARCHAR(64)   NOT NULL COMMENT '唯一编码',
    display_name  VARCHAR(100)  NOT NULL COMMENT '显示名称',
    type          VARCHAR(32)   NULL     COMMENT '流程类型',
    state         INT           NULL DEFAULT 1 COMMENT '流程是否可用(1可用；0不可用)',
    content       BLOB          NULL     COMMENT '流程模型定义',
    version       INT           NULL DEFAULT 1 COMMENT '版本',
    create_time   TIMESTAMP     NULL     COMMENT '创建时间',
    create_user   VARCHAR(64)   NULL     COMMENT '创建用户',
    update_time   TIMESTAMP     NULL     COMMENT '更新时间',
    update_user   VARCHAR(64)   NULL     COMMENT '更新用户',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_instance (
    id                 BIGINT        NOT NULL COMMENT '主键',
    parent_id          BIGINT        NULL     COMMENT '父流程ID，子流程实例才有值',
    process_define_id  BIGINT        NOT NULL COMMENT '流程定义ID',
    state              INT           NULL     COMMENT '流程实例状态(10：进行中；20：已完成；30：已撤回；40：强行中止；50：挂起；99：已废弃)',
    parent_node_name   VARCHAR(100)  NULL     COMMENT '父流程依赖的节点名称',
    business_no        VARCHAR(64)   NULL     COMMENT '业务编号',
    operator           VARCHAR(64)   NULL     COMMENT '流程发起人',
    expire_time        TIMESTAMP     NULL     COMMENT '期望完成时间',
    variable           TEXT          NULL     COMMENT '附属变量json存储',
    create_time        TIMESTAMP     NULL     COMMENT '创建时间',
    create_user        VARCHAR(64)   NULL     COMMENT '创建用户',
    update_time        TIMESTAMP     NULL     COMMENT '更新时间',
    update_user        VARCHAR(64)   NULL     COMMENT '更新用户',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_task (
    id                   BIGINT        NOT NULL COMMENT '主键',
    process_instance_id  BIGINT        NOT NULL COMMENT '流程实例ID',
    task_name            VARCHAR(100)  NOT NULL COMMENT '任务名称编码',
    display_name         VARCHAR(100)  NOT NULL COMMENT '任务显示名称',
    task_type            INT           NULL     COMMENT '任务类型(0：主办任务；1：协办任务)',
    perform_type         INT           NULL     COMMENT '参与类型(0：普通参与；1：会签参与)',
    task_state           INT           NULL     COMMENT '任务状态(10：进行中；20：已完成；30：已撤回；40：强行中止；50：挂起；99：已废弃)',
    operator             VARCHAR(64)   NULL     COMMENT '任务处理人',
    finish_time          TIMESTAMP     NULL     COMMENT '任务完成时间',
    expire_time          TIMESTAMP     NULL     COMMENT '任务期待完成时间',
    form_key             VARCHAR(100)  NULL     COMMENT '任务处理表单KEY',
    task_parent_id       BIGINT        NULL     COMMENT '父任务ID',
    variable             TEXT          NULL     COMMENT '附属变量json存储',
    create_time          TIMESTAMP     NULL     COMMENT '创建时间',
    create_user          VARCHAR(64)   NULL     COMMENT '创建用户',
    update_time          TIMESTAMP     NULL     COMMENT '更新时间',
    update_user          VARCHAR(64)   NULL     COMMENT '更新用户',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_task_actor (
    id               BIGINT       NOT NULL COMMENT '主键',
    process_task_id  BIGINT       NOT NULL COMMENT '流程任务ID',
    actor_id         VARCHAR(64)  NOT NULL COMMENT '参与者ID',
    create_time      TIMESTAMP    NULL     COMMENT '创建时间',
    create_user      VARCHAR(64)  NULL     COMMENT '创建用户',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_cc_instance (
    id                   BIGINT       NOT NULL COMMENT '主键',
    process_instance_id  BIGINT       NOT NULL COMMENT '流程实例ID',
    actor_id             VARCHAR(64)  NOT NULL COMMENT '被抄送人ID',
    state                INT          NULL DEFAULT 0 COMMENT '抄送状态(1:已读；0：未读)',
    create_time          TIMESTAMP    NULL     COMMENT '创建时间',
    create_user          VARCHAR(64)  NULL     COMMENT '创建用户',
    update_time          TIMESTAMP    NULL     COMMENT '更新时间',
    update_user          VARCHAR(64)  NULL     COMMENT '更新用户',
    PRIMARY KEY (id)
);
