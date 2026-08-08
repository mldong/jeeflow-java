CREATE TABLE IF NOT EXISTS wf_process_define (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '唯一编码',
    display_name VARCHAR(100) NOT NULL COMMENT '显示名称',
    type VARCHAR(32) NULL COMMENT '流程类型',
    state INT NULL DEFAULT 1 COMMENT '流程是否可用',
    content BLOB NULL COMMENT '流程模型定义',
    version INT NULL DEFAULT 1 COMMENT '版本',
    create_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    update_time TIMESTAMP NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_instance (
    id BIGINT NOT NULL COMMENT '主键',
    parent_id BIGINT NULL COMMENT '父流程ID',
    process_define_id BIGINT NOT NULL COMMENT '流程定义ID',
    state INT NULL COMMENT '10进行中 20已完成 30已撤回 40强行中止 50挂起 99已废弃',
    parent_node_name VARCHAR(100) NULL,
    business_no VARCHAR(64) NULL,
    operator VARCHAR(64) NULL,
    expire_time TIMESTAMP NULL,
    variable TEXT NULL COMMENT '附属变量json',
    create_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    update_time TIMESTAMP NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_task (
    id BIGINT NOT NULL COMMENT '主键',
    process_instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称编码',
    display_name VARCHAR(100) NOT NULL COMMENT '任务显示名称',
    task_type INT NULL COMMENT '0主办 1协办',
    perform_type INT NULL COMMENT '0普通 1会签',
    task_state INT NULL COMMENT '10进行中 20已完成',
    operator VARCHAR(64) NULL,
    finish_time TIMESTAMP NULL,
    expire_time TIMESTAMP NULL,
    form_key VARCHAR(100) NULL,
    task_parent_id BIGINT NULL,
    variable TEXT NULL,
    create_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    update_time TIMESTAMP NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_task_actor (
    id BIGINT NOT NULL COMMENT '主键',
    process_task_id BIGINT NOT NULL COMMENT '任务ID',
    actor_id VARCHAR(64) NOT NULL COMMENT '参与者ID',
    create_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_process_cc_instance (
    id BIGINT NOT NULL COMMENT '主键',
    process_instance_id BIGINT NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    state INT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    update_time TIMESTAMP NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

-- ── 扩展表（design/surrogate，对齐 jeeflow-repository-jdbc schema-h2.sql）──

CREATE TABLE IF NOT EXISTS wf_process_design (
    id            BIGINT       NOT NULL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    type          VARCHAR(50)  DEFAULT 'approval',
    icon          VARCHAR(200),
    is_deployed   INT          DEFAULT 0,
    remark        CLOB,
    create_time   TIMESTAMP,
    create_user   VARCHAR(64),
    update_time   TIMESTAMP,
    update_user   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_process_design_name ON wf_process_design(name);

CREATE TABLE IF NOT EXISTS wf_process_design_his (
    id                BIGINT NOT NULL PRIMARY KEY,
    process_design_id BIGINT NOT NULL,
    content           BLOB,
    create_time       TIMESTAMP,
    create_user       VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_process_design_his_pdid ON wf_process_design_his(process_design_id);

CREATE TABLE IF NOT EXISTS wf_process_surrogate (
    id            BIGINT      NOT NULL PRIMARY KEY,
    process_name  VARCHAR(100),
    operator      VARCHAR(64) NOT NULL,
    surrogate     VARCHAR(64) NOT NULL,
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    enabled       INT         DEFAULT 1,
    create_time   TIMESTAMP,
    create_user   VARCHAR(64),
    update_time   TIMESTAMP,
    update_user   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_process_surrogate_op ON wf_process_surrogate(operator);
CREATE INDEX IF NOT EXISTS idx_process_surrogate_sur ON wf_process_surrogate(surrogate);
