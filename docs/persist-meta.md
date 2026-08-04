# 元数据驱动入库（persist-meta）

> issues/23 · 1.7.0 起随 jeeflow-persist 提供

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

## 测试

```bash
mvn -pl jeeflow-persist test   # 26 用例：模型/加载器 + NORMAL·JSON 读写 + EXPAND·子表全链路（H2 内存库）
```
