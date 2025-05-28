package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeleteDeliveryConfigTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;
  private ObjectMapper objectMapper;

  @Autowired
  public DeleteDeliveryConfigTask(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);

    if (stageData.deliveryConfigId == null) {
      throw new IllegalArgumentException("Key 'deliveryConfigId' must be provided.");
    }

    Optional<DeliveryConfig> config = getDeliveryConfig(stageData.deliveryConfigId);

    if (!config.isPresent()) {
      log.debug(
          "Config {} does not exist, considering deletion successful.", stageData.deliveryConfigId);
      return TaskResult.SUCCEEDED;
    }

    try {
      log.debug("Deleting delivery config: " + objectMapper.writeValueAsString(config.get()));
    } catch (JsonProcessingException e) {
      // ignore
    }

    front50Service.deleteDeliveryConfig(config.get().getApplication(), stageData.deliveryConfigId);

    return TaskResult.SUCCEEDED;
  }

  public Optional<DeliveryConfig> getDeliveryConfig(String id) {
    try {
      DeliveryConfig deliveryConfig =
          Retrofit2SyncCall.execute(front50Service.getDeliveryConfig(id));
      return Optional.of(deliveryConfig);
    } catch (SpinnakerHttpException e) {
      // ignore an unknown (404) or unauthorized (403, 401)
      if (Arrays.asList(404, 403, 401).contains(e.getResponseCode())) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  private static class StageData {
    public String deliveryConfigId;
  }
}
