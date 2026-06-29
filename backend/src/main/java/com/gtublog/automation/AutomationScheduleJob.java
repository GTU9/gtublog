package com.gtublog.automation;

import java.time.Instant;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class AutomationScheduleJob implements Job {

    static final String SCHEDULE_ID_KEY = "scheduleId";

    private final AutomationAdminService automationAdminService;

    public AutomationScheduleJob(AutomationAdminService automationAdminService) {
        this.automationAdminService = automationAdminService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getMergedJobDataMap();
            Long scheduleId = dataMap.getLongValue(SCHEDULE_ID_KEY);
            Instant fireTime = context.getScheduledFireTime() == null
                    ? Instant.now()
                    : context.getScheduledFireTime().toInstant();
            automationAdminService.triggerScheduledRun(scheduleId, null, fireTime);
        } catch (Exception exception) {
            throw new JobExecutionException(exception);
        }
    }
}
