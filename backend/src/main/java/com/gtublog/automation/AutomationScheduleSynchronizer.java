package com.gtublog.automation;

import static org.quartz.CronScheduleBuilder.cronSchedule;

import java.time.ZoneId;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduleSynchronizer {

    private static final String GROUP = "gtublog-automation";

    private final Scheduler scheduler;

    public AutomationScheduleSynchronizer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void synchronize(AutomationSchedule schedule) {
        try {
            var jobKey = org.quartz.JobKey.jobKey("schedule-" + schedule.getId(), GROUP);
            var triggerKey = org.quartz.TriggerKey.triggerKey("schedule-" + schedule.getId(), GROUP);

            scheduler.unscheduleJob(triggerKey);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            if (!schedule.isActive()) {
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(AutomationScheduleJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(AutomationScheduleJob.SCHEDULE_ID_KEY, schedule.getId())
                    .build();

            var cron = cronSchedule(toQuartzCron(schedule.getCronExpression()))
                    .inTimeZone(java.util.TimeZone.getTimeZone(ZoneId.of(schedule.getTimezone())));
            if ("DO_NOTHING".equalsIgnoreCase(schedule.getMisfirePolicy())) {
                cron = cron.withMisfireHandlingInstructionDoNothing();
            } else {
                cron = cron.withMisfireHandlingInstructionFireAndProceed();
            }

            var trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withSchedule(cron)
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to synchronize Quartz schedule.", exception);
        }
    }

    public void remove(Long scheduleId) {
        try {
            var jobKey = org.quartz.JobKey.jobKey("schedule-" + scheduleId, GROUP);
            var triggerKey = org.quartz.TriggerKey.triggerKey("schedule-" + scheduleId, GROUP);
            scheduler.unscheduleJob(triggerKey);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to remove Quartz schedule.", exception);
        }
    }

    public void clearAutomationSchedules() {
        try {
            for (var jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to clear automation schedules.", exception);
        }
    }

    private String toQuartzCron(String cronExpression) {
        var fields = cronExpression.trim().split("\\s+");
        if (fields.length != 6) {
            return cronExpression;
        }

        var dayOfMonth = fields[3];
        var dayOfWeek = fields[5];

        if (!"?".equals(dayOfMonth) && !"?".equals(dayOfWeek)) {
            if ("*".equals(dayOfWeek)) {
                fields[5] = "?";
            } else {
                fields[3] = "?";
            }
        }

        return String.join(" ", fields);
    }
}
