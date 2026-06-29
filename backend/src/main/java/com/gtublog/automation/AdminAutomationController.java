package com.gtublog.automation;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/automation")
public class AdminAutomationController {

    private final AutomationAdminService automationAdminService;

    public AdminAutomationController(AutomationAdminService automationAdminService) {
        this.automationAdminService = automationAdminService;
    }

    @GetMapping("/topics")
    public List<AutomationTopicResponse> topics() {
        return automationAdminService.topics();
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public AutomationTopicResponse createTopic(@Valid @RequestBody AutomationTopicRequest request) {
        return automationAdminService.createTopic(request);
    }

    @PutMapping("/topics/{topicId}")
    public AutomationTopicResponse updateTopic(@PathVariable Long topicId, @Valid @RequestBody AutomationTopicRequest request) {
        return automationAdminService.updateTopic(topicId, request);
    }

    @GetMapping("/topics/{topicId}/sources")
    public List<AutomationSourceResponse> sources(@PathVariable Long topicId) {
        return automationAdminService.sources(topicId);
    }

    @PostMapping("/topics/{topicId}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public AutomationSourceResponse createSource(@PathVariable Long topicId, @Valid @RequestBody AutomationSourceRequest request) {
        return automationAdminService.createSource(topicId, request);
    }

    @PutMapping("/sources/{sourceId}")
    public AutomationSourceResponse updateSource(@PathVariable Long sourceId, @Valid @RequestBody AutomationSourceRequest request) {
        return automationAdminService.updateSource(sourceId, request);
    }

    @DeleteMapping("/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable Long sourceId) {
        automationAdminService.deleteSource(sourceId);
    }

    @GetMapping("/topics/{topicId}/schedules")
    public List<AutomationScheduleResponse> schedules(@PathVariable Long topicId) {
        return automationAdminService.schedules(topicId);
    }

    @PostMapping("/topics/{topicId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public AutomationScheduleResponse createSchedule(@PathVariable Long topicId, @Valid @RequestBody AutomationScheduleRequest request) {
        return automationAdminService.createSchedule(topicId, request);
    }

    @PutMapping("/schedules/{scheduleId}")
    public AutomationScheduleResponse updateSchedule(@PathVariable Long scheduleId, @Valid @RequestBody AutomationScheduleRequest request) {
        return automationAdminService.updateSchedule(scheduleId, request);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable Long scheduleId) {
        automationAdminService.deleteSchedule(scheduleId);
    }

    @GetMapping("/runs")
    public List<AutomationRunResponse> runs() {
        return automationAdminService.runs();
    }

    @GetMapping("/runs/{runId}")
    public AutomationRunDetailResponse run(@PathVariable Long runId) {
        return automationAdminService.runDetail(runId);
    }

    @GetMapping("/diagnostics")
    public AutomationDiagnosticsResponse diagnostics() {
        return automationAdminService.diagnostics();
    }

    @GetMapping("/outbox")
    public List<AutomationOutboxResponse> outbox() {
        return automationAdminService.outbox();
    }

    @PostMapping("/outbox/process")
    public void processOutbox() {
        automationAdminService.processOutbox();
    }

    @PostMapping("/topics/{topicId}/runs/manual")
    public AutomationRunResponse triggerManualRun(@PathVariable Long topicId, @Valid @RequestBody(required = false) AutomationRunRequest request) {
        return automationAdminService.triggerManualRun(topicId, request == null ? null : request.idempotencyKey());
    }
}
