package com.mldong.jeeflow.util;

/**
 * 字符串工具（零依赖，替代 Hutool StrUtil 的少量方法）
 *
 * @author mldong
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isBlank(CharSequence cs) {
        if (cs == null) return true;
        for (int i = 0, len = cs.length(); i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 归属值（actor id）集合归一——<b>全栈唯一的一枚尺子</b>
     * （issues/141 G10 · spec 06 §2.10 抄送侧落地，issues/142 B 批 · spec 06 §2.11 逐字搬到任务侧）。
     *
     * <p>判据（逗号串与数组<b>两形同判</b>）：逐元素 trim，<b>空串与纯空白丢弃</b>，
     * 同一次调用内的重复折叠（顺序保持）。丢完为空 ⇒ 调用方不得建行、也不得 fire 码 4。</p>
     *
     * <p>判空<b>一律</b> {@code trim().isEmpty()}：严禁借语言自带的假值判据
     * （{@code "0"} 这类"看起来像空"的正常 id 必须留下，且 {@code "0"} 与 {@code "00"}
     * 是两个不同的人）。落库与比较一律取 trim 后的值——{@code " 123 "} 与 {@code "123"}
     * 判为同一个人，才与写侧判重咬合（不然同一人落两行）。</p>
     *
     * <p>为什么这一支必须只有一枚：{@code actor_id} 是归属列，空归属值正是 issues/129 那族
     * "空 operator 读全库"的病根；抄送表与参与者表是同一族病灶的两张表。判据抄第二份迟早分叉
     * （php 本轮实测到"归一函数内部严格比较、仓储写侧却用松散 {@code in_array}"）。</p>
     *
     * @param raw 原始归属值数组，元素可为 {@code null}（{@code null} 丢弃，<b>不得</b>串化成 {@code "null"}）
     * @return 归一后的归属值列表（保序、去重、无空值）；入参为 {@code null} 时返回空列表
     */
    public static java.util.List<String> normalizeActors(String... raw) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (raw == null) return out;
        for (String actorId : raw) {
            if (actorId == null) continue;
            String trimmed = actorId.trim();
            if (trimmed.isEmpty()) continue;
            if (!out.contains(trimmed)) out.add(trimmed);
        }
        return out;
    }

    /**
     * 入参形态收敛＋归一：门面/引擎/handler 三条腿的 actor 入参（{@code actorIds}／
     * {@code f_ccActors}／{@code tf_ccActors}／{@code f_nextNodeOperator}／{@code tf_nextNodeOperator}）
     * 一律过这一支——<b>逗号串与数组两形走同一枚尺子</b>（{@link #normalizeActors}）。
     *
     * <ul>
     *   <li>{@code java.util.Collection} ⇒ 逐元素收敛为字符串：{@code null} 元素<b>留 null</b>
     *       交给尺子丢弃（严禁 {@code String.valueOf(null)} 变成 {@code "null"} 落进归属列，
     *       也严禁 {@code Object::toString} 在 null 元素上抛 NPE）；数字元素收敛成字符串后照样 trim；</li>
     *   <li>{@code String} ⇒ 按逗号切分（含 java 的 {@code "".split(",")} 得到一个空元素那个坑——
     *       空元素由尺子丢掉，因此空串入参得到空集合）；</li>
     *   <li>其它标量 ⇒ 视作单个字符串（与 {@code toStringList} 逗号串腿同档）；</li>
     *   <li>{@code null} ⇒ 空集合。</li>
     * </ul>
     *
     * @param raw 原始入参（数组/集合、逗号串、标量或 null）
     * @return 归一后的归属值列表（保序、去重、无空值），永不为 {@code null}
     */
    public static java.util.List<String> normalizeActorArg(Object raw) {
        if (raw == null) return new java.util.ArrayList<String>();
        if (raw instanceof java.util.Collection) {
            java.util.Collection<?> coll = (java.util.Collection<?>) raw;
            String[] values = new String[coll.size()];
            int i = 0;
            for (Object o : coll) values[i++] = o == null ? null : String.valueOf(o);
            return normalizeActors(values);
        }
        if (raw instanceof String) {
            // java 坑位："".split(",") 得到的是「一个空元素」而不是零个 ⇒ 旧形状落出 actor_id='' 的行。
            // 这里不特判空串，交给 normalizeActors 丢元素，两形因此共用同一条判据。
            return normalizeActors(((String) raw).split(","));
        }
        return normalizeActors(String.valueOf(raw));
    }

    /**
     * 单个归属值归一（标量档，与 {@link #normalizeActors} 同一条尺子）：trim；
     * 空串/纯空白/{@code null} ⇒ 返回 {@code null}，由调用方按"缺参数"档处理。
     *
     * <p>用于 {@code processTask/transfer} 的 {@code fromActor}/{@code toActor} 这类
     * <b>必填</b>归属值：spec 06 §2.11 要求"落库与比较一律取 trim 后的值"——
     * 摘人那一条 {@code DELETE} 与判据用的 {@code contains} 都拿原值比的话，
     * {@code " leader "} 与库里的 {@code "leader"} 判不成同一个人。</p>
     *
     * <p>判空仍是 {@code trim().isEmpty()}：{@code "0"} 是合法 id，不得当成空。</p>
     *
     * @param raw 原始值（可为 null）
     * @return trim 后的值；空串/纯空白/null 返回 {@code null}
     */
    public static String normalizeActor(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 归属值<b>删除腿</b>展开（issues/137 §3-6 · spec 06 §processTask/removeTaskActor 语义 6，
     * owner 2026-10-02 拍「两形并集」）：把待删列表展开成 {@code DELETE ... IN (...)} 真正要绑的值——
     * <b>空值一律丢弃，非空值同时保留「原值」与「trim 值」两形</b>（去重、保序）。
     *
     * <p>为什么必须两形、只取一头各有一种假成功（1.8.36 之前八栈正好分成这两派，没有一处两全）：</p>
     * <ul>
     *   <li>只取 <b>trim 值</b>（php/csharp/rust/moon 四栈八处的旧形状）⇒ 门面按语义 6 交出的历史脏行
     *       原值 {@code " 9101 "} 被削成 {@code 9101}，真库 NO PAD 排序规则下那一行删不掉，
     *       门面却报成功——被摘的人待办还在；</li>
     *   <li>只取 <b>原值</b>（go/node/python/java 四栈九处的旧形状）⇒ 第三方绕过门面直连仓储传
     *       {@code " 8601 "} 时删不掉写侧归一后落库的规范行 {@code 8601}（issues/142 §9.2 那一路）；
     *       且空值照喂 {@code DELETE}，会把历史 {@code actor_id=''} 脏行批量误删
     *       （那是替脏数据做掉唯一痕迹）。</li>
     * </ul>
     *
     * <p>两形并集同时满足两侧：脏行按原值命中、规范行按 trim 形命中。按 §2.11 归一口径
     * {@code " 9101 "} 与 {@code 9101} 本就是<b>同一个人</b>，两行都删掉才是"摘掉这个人"的正确结果，
     * 不构成误删。</p>
     *
     * <p>判空<b>一律</b> {@code trim().isEmpty()}（与 {@link #normalizeActors} 同一枚尺子；本方法只加
     * "原值也进集合"这一层，<b>不抄第二份 trim/判空判据</b>）：{@code "0"} 是合法 id 必须留下，
     * 且 {@code "0"} 与 {@code "00"} 是两个人。</p>
     *
     * @param raw 待删归属值数组，元素可为 {@code null}（{@code null} 丢弃，<b>不得</b>串化成 {@code "null"}）
     * @return 展开后的删除值列表（保序、去重、无空值）；入参为 {@code null} 或全为空值时返回<b>空列表</b>
     *         ——调用方据此早退，<b>一条 {@code DELETE} 都不发</b>（空列表不得退化成"清空该任务全部参与者"）
     */
    public static java.util.List<String> actorDeleteForms(String... raw) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (raw == null) return out;
        for (String actorId : raw) {
            if (actorId == null) continue;
            String trimmed = actorId.trim();
            if (trimmed.isEmpty()) continue;                  // ① 空值丢弃，不喂 DELETE
            if (!out.contains(actorId)) out.add(actorId);      // ② 原值形：保住未 trim 的历史脏行
            if (!out.contains(trimmed)) out.add(trimmed);      // ② trim 形：保住写侧归一后的规范行
        }
        return out;
    }

    /**
     * 抄送人集合归一（issues/141 G10 已落地的公开入口，<b>保留原名转发到 {@link #normalizeActors}</b>）。
     *
     * <p>spec 06 §2.11 要求任务侧复用同一枚单点、不要再抄第二份；名字里的 "Cc" 已不贴合
     * 实际覆盖面，故新增 {@code normalizeActors} 作通用名，本成员原样转发维持 API 面不破。</p>
     *
     * @deprecated 语义已扩到全部归属值（抄送／参与者／nextNodeOperator），改用
     *             {@link #normalizeActors(String...)} 或 {@link #normalizeActorArg(Object)}；
     *             本转发保留至下一大版本。
     */
    @Deprecated
    public static java.util.List<String> normalizeCcActors(String... raw) {
        return normalizeActors(raw);
    }

    /**
     * 简单格式化：用 {} 占位符替换参数
     */
    public static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) return template;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        for (int i = 0, len = template.length(); i < len; i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < len && template.charAt(i + 1) == '}') {
                sb.append(argIdx < args.length ? (args[argIdx] != null ? args[argIdx] : "null") : "{}");
                argIdx++;
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 首字母小写
     */
    public static String lowerFirst(CharSequence str) {
        if (str == null || str.length() == 0) return "";
        if (str.length() == 1) return str.toString().toLowerCase();
        char first = Character.toLowerCase(str.charAt(0));
        return first + str.subSequence(1, str.length()).toString();
    }
}
