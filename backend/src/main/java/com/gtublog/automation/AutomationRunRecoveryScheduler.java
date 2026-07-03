package com.gtublog.automation;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AutomationRunRecoveryScheduler implements ApplicationRunner {

    private static final JobKey JOB_KEY = JobKey.jobKey("expired-run-recovery", "automation-maintenance");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("expired-run-recovery", "automation-maintenance");

    private final Scheduler scheduler;
    private final AutomationProperties automationProperties;

    public AutomationRunRecoveryScheduler(Scheduler scheduler, AutomationProperties automationProperties) {
        this.scheduler = scheduler;
        this.automationProperties = automationProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (scheduler.checkExists(TRIGGER_KEY)) {
                scheduler.unscheduleJob(TRIGGER_KEY);
            }
            if (scheduler.checkExists(JOB_KEY)) {
                scheduler.deleteJob(JOB_KEY);
            }
            var job = newJob(AutomationRunRecoveryJob.class)
                    .withIdentity(JOB_KEY)
                    .storeDurably()
                    .build();
            scheduler.addJob(job, true);
            var trigger = newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .forJob(JOB_KEY)
                    .startNow()
                    .withSchedule(simpleSchedule()
                            .withIntervalInMilliseconds(automationProperties.run().recoveryInterval().toMillis())
                            .repeatForever())
                    .build();
            scheduler.scheduleJob(trigger);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Could not schedule automation run recovery.", exception);
        }
    }
}
