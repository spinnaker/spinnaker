package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;

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
  public TaskResult execute(@Nonnull Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);

    if (stageData.deliveryConfigId == null) {
      throw new IllegalArgumentException("Key 'deliveryConfigId' must be provided.");
    }

    Optional<DeliveryConfig> config = getDeliveryConfig(stageData.deliveryConfigId);

    if (!config.isPresent()) {
      log.debug("Config {} does not exist, considering deletion successful.", stageData.deliveryConfigId);
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
      DeliveryConfig deliveryConfig = front50Service.getDeliveryConfig(id);
      return Optional.of(deliveryConfig);
    } catch (RetrofitError e) {
      //ignore an unknown (404) or unauthorized (403, 401)
      if (e.getResponse() != null && Arrays.asList(404, 403, 401).contains(e.getResponse().getStatus())) {
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
