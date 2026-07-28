package com.mldong.jeeflow;

import com.mldong.jeeflow.enums.WfErrEnum;

/**
 * 工作流引擎异常
 *
 * @author mldong
 */
public class JeeflowException extends RuntimeException {

    private final int code;

    public JeeflowException(String message) {
        super(message);
        this.code = -1;
    }

    public JeeflowException(int code, String message) {
        super(message);
        this.code = code;
    }

    public JeeflowException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public JeeflowException(WfErrEnum err) {
        super(err.getMessage());
        this.code = err.getCode();
    }

    public int getCode() {
        return code;
    }
}
