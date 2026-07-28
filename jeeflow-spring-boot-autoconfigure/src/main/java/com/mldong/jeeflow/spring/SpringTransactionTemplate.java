package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.ITransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring 事务模板的实现
 *
 * @author mldong
 */
public class SpringTransactionTemplate implements ITransactionTemplate {

    private final TransactionTemplate txTemplate;

    public SpringTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(Supplier<T> action) {
        try {
            return txTemplate.execute(status -> {
                try {
                    return action.get();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
