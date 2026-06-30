package com.gtublog.automation;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class AutomationRunRecoveryJob implements Job {

    private final AutomationRunRecoveryService recoveryService;

    public AutomationRunRecoveryJob(AutomationRunRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            recoveryService.recoverExpiredRuns();
        } catch (Exception exception) {
            throw new JobExecutionException(exception, false);
        }
    }
}
