# 元数据驱动入库（persist-meta）

> issues/23 起 · 1.8.0（1.7.0 引入）随 jeeflow-persist 提供

**字段元数据（storageType）驱动的动态写入/读取**——复杂表单（对象/JSON/子表）落库与
流程回显成为通用能力，不绑定任何框架低代码设施。规范见文档站《10 · 元数据驱动的动态写入/读取》；
本页是 Java 语言视角。

## 元数据 JSON（`persist-meta/biz_leave.json`，classpath 或文件系统）

```json
{
  "tableName": "biz_leave",
  "primaryKey": "id",
  "fields": [
    { "name": "companyName", "columnName": "company_name" },
    { "name": "address", "storageType": "EXPAND",
      "expandFields": { "province": "province", "city": "city", "detail": "detail_addr" } },
    { "name": "extra", "storageType": "JSON" },
    { "name": "items", "storageType": "ONE2MANY",
      "targetTable": "biz_leave_item", "foreignKey": "leave_id" }
  ]
}
```

storageType 支持名称（"EXPAND"）或数字（2，mldong dev_schema_field 1-5 语义）。


**JSON 配置字段语义**（顶层 + `fields[]`，storageType 名称/数字双解析）：

| 字段 | 类型 | 缺省 | 语义 |
|------|------|------|------|
| `tableName` | string | 必填 | 业务表名（应与 JSON 文件名一致） |
| `primaryKey` | string | `id` | 主键列名（子表外键缺省回落它） |
| `fields[].name` | string | 必填 | **表单字段名**（`f_` 去前缀后的名字，如 `f_title` → `title`） |
| `fields[].columnName` | string | name 转下划线 | 主表列名 |
| `fields[].storageType` | string \| number | `NORMAL` | `NORMAL`(1) 直写 / `EXPAND`(2) 展开 / `JSON`(3) 序列化 / `ONE2ONE`(4) 子表单条 / `ONE2MANY`(5) 子表多条（对齐 mldong dev_schema_field） |
| `fields[].expandFields` | object | 无 | **EXPAND 专用**：子字段名 → 表列名映射 |
| `fields[].targetTable` | string | 无 | **ONE2ONE/ONE2MANY 专用**：子表表名 |
| `fields[].foreignKey` | string | 主表主键列名 | **ONE2ONE/ONE2MANY 专用**：子表外键列 |


## 装配（写侧替换默认 writer + 读侧）

```java
JdbcDynamicTableWriter base = new JdbcDynamicTableWriter(dataSource);
base.setPrimaryKeyGenerator(IdWorker::getId);          // 雪花主键（非自增表，子表外键依赖）
IDynamicMetaProvider provider = new JsonMetaProvider(); // classpath persist-meta/；或 new JsonMetaProvider("/etc/jeeflow/persist-meta", "persist-meta")
ServiceContext.put("dynamicTableWriter", new MetaTableWriter(base, provider));   // 拦截器模型级挂载不变

// 读侧（流程详情接口回显）
MetaTableReader reader = new MetaTableReader(new JdbcTableReader(dataSource), provider);
```

无元数据的表自动回落 1.6.x 行为（零破坏）；`IDynamicMetaProvider` 也可自行实现
（如把 mldong dev_schema 映射为 storageType 语义）。

## 回显

```java
Map<String, Object> form = reader.readByProcessInstance("biz_leave", processInstanceId);
// form.address = { province, city, detail }   EXPAND 反展开
// form.extra   = { tag, level }               JSON 反序列化
// form.items   = [ { name, qty }, ... ]       ONE2MANY 子表组装
// form.process_instance_id / apply_user_id    原样带出（小写键）
```

边界（不做）：通用分页/条件/权限/排序——集成方查询体系领域。


## 1.8.0 增强（SYNC 同步演进协同）

- **中途更新（`update`）**：按元数据 storageType 组装 SET 列——NORMAL/JSON/EXPAND 参与更新；
  **ONE2ONE/ONE2MANY 子表不参与中途更新**（SYNC 任务推进只更新主表行状态，子表数据变动走重新提交）
- **子表系统用户字段（issues/24）**：子表递归插入继承主表 `apply_user_id`（= 流程 operator，
  子表单显式同名字段优先）——BIGINT `create_user`/`update_user` 列不再回落 "system" 严格模式报错
- **回显去冗余（issues/24）**：EXPAND 展开列（如 `province`/`city`）已消费为对象（`address`），
  不再作为顶层平铺键重复带出

## 测试

```bash
mvn -pl jeeflow-persist test   # 26 用例：模型/加载器 + NORMAL·JSON 读写 + EXPAND·子表全链路（H2 内存库）
```
