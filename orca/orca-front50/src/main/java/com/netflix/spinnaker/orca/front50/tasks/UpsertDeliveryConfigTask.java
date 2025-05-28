package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.DeliveryConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertDeliveryConfigTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private Front50Service front50Service;
  private ObjectMapper objectMapper;

  @Autowired
  public UpsertDeliveryConfigTask(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    if (!stage.getContext().containsKey("delivery")) {
      throw new IllegalArgumentException("Key 'delivery' must be provided.");
    }

    // todo eb: base64 encode this if it will have expressions
    DeliveryConfig deliveryConfig =
        objectMapper.convertValue(
            stage.getContext().get("delivery"), new TypeReference<DeliveryConfig>() {});

    DeliveryConfig savedConfig;
    if (configExists(deliveryConfig.getId())) {
      savedConfig =
          Retrofit2SyncCall.execute(
              front50Service.updateDeliveryConfig(deliveryConfig.getId(), deliveryConfig));
    } else {
      savedConfig = Retrofit2SyncCall.execute(front50Service.createDeliveryConfig(deliveryConfig));
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("application", deliveryConfig.getApplication());
    outputs.put("deliveryConfig", savedConfig);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  private boolean configExists(String id) {
    if (id == null) {
      return false;
    }
    try {
      front50Service.getDeliveryConfig(id);
      return true;
    } catch (SpinnakerHttpException e) {
      if (Arrays.asList(404, 403, 401).contains(e.getResponseCode())) {
        return false;
      } else {
        throw e;
      }
    }
  }
}
