package com.mldong.jeeflow.util;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.spi.IUserProvider;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程工具类（去 Hutool 依赖）
 *
 * @author mldong
 */
public final class FlowUtil {

    private FlowUtil() {}

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 追加用户信息到流程参数 */
    public static void addUserInfoToArgs(String operator, FlowData args, IUserProvider userProvider) {
        if (userProvider == null) return;
        args.put(FlowConst.USER_USER_ID, operator);
        args.put(FlowConst.USER_REAL_NAME, userProvider.getRealName(operator));
        args.put(FlowConst.USER_DEPT_ID, userProvider.getDeptId(operator));
        args.put(FlowConst.USER_DEPT_NAME, userProvider.getDeptName(operator));
        args.put(FlowConst.USER_POST_ID, userProvider.getPostId(operator));
        args.put(FlowConst.USER_POST_NAME, userProvider.getPostName(operator));
    }

    /** 自动构造标题 */
    public static void addAutoGenTitle(String displayName, FlowData args) {
        String realName = args.getStr(FlowConst.USER_REAL_NAME, "");
        String title = realName + "的" + displayName + "-" + DATE_FORMAT.format(new Date());
        args.put(FlowConst.AUTO_GEN_TITLE, title);
    }

    /** 判断是否为第一个任务节点（开始节点的直接后继） */
    public static boolean isFirstTaskName(ProcessModel model, String taskName) {
        AtomicBoolean result = new AtomicBoolean(false);
        model.getStart().getOutputs().forEach(tm -> {
            if (tm.getTo().equalsIgnoreCase(taskName)) {
                result.set(true);
            }
        });
        return result.get();
    }

    /** 解析期待完成时间 */
    public static java.time.LocalDateTime processTime(String expireTime, FlowData args) {
        if (args.containsKey(expireTime)) {
            Object obj = args.get(expireTime);
            if (obj instanceof Date) {
                return toLocalDateTime((Date) obj);
            } else if (obj instanceof Long) {
                return toLocalDateTime(new Date((Long) obj));
            } else if (obj instanceof String) {
                try {
                    return toLocalDateTime(DATE_FORMAT.parse((String) obj));
                } catch (ParseException e) {
                    return null;
                }
            }
        }
        if (StringUtils.isNotBlank(expireTime)) {
            Date now = new Date();
            if (expireTime.endsWith("s")) {
                int seconds = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + seconds * 1000L));
            } else if (expireTime.endsWith("m")) {
                int minutes = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + minutes * 60000L));
            } else if (expireTime.endsWith("h")) {
                int hours = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + hours * 3600000L));
            } else if (expireTime.endsWith("d")) {
                int days = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.DAY_OF_MONTH, days);
                return toLocalDateTime(cal.getTime());
            }
            try {
                return toLocalDateTime(DATE_FORMAT.parse(expireTime));
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    private static java.time.LocalDateTime toLocalDateTime(Date date) {
        return java.time.LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }
}
