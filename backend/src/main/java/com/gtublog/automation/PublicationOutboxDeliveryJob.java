package com.gtublog.automation;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class PublicationOutboxDeliveryJob implements Job {

    private final PublicationOutboxService publicationOutboxService;

    public PublicationOutboxDeliveryJob(PublicationOutboxService publicationOutboxService) {
        this.publicationOutboxService = publicationOutboxService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            publicationOutboxService.processPendingEvents();
        } catch (Exception exception) {
            throw new JobExecutionException(exception);
        }
    }
}
