package com.mldong.jeeflow.interceptor;

import com.mldong.jeeflow.domain.Candidate;
import com.mldong.jeeflow.model.TaskModel;

import java.util.List;

/**
 * 候选人处理接口
 *
 * @author mldong
 */
public interface CandidateHandler {

    List<Candidate> handle(TaskModel taskModel);
}
