package com.netflix.spinnaker.orca.webhook.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.service.RestTemplateData;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreateWebhookTask implements RetryableTask {
  private final WebhookService webhookService;
  private final WebhookProperties webhookProperties;
  private final ObjectMapper objectMapper;

  @Autowired
  public CreateWebhookTask(
      WebhookService webhookService,
      WebhookProperties webhookProperties,
      ObjectMapper objectMapper) {
    this.webhookService = webhookService;
    this.webhookProperties = webhookProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  public TaskResult execute(StageExecution stage) {

    WebhookResponseProcessor responseProcessor =
        new WebhookResponseProcessor(objectMapper, stage, webhookProperties);
    RestTemplateData restTemplateData =
        webhookService.getRestTemplateData(WebhookService.WebhookTaskType.CREATE, stage);
    try {
      var response = webhookService.callWebhook(restTemplateData);
      return responseProcessor.process(restTemplateData, response, null);
    } catch (Exception e) {
      return responseProcessor.process(restTemplateData, null, e);
    }
  }

  public long getBackoffPeriod() {
    return 10_000;
  }

  public long getTimeout() {
    return 300_000;
  }
}
