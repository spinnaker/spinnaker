package com.netflix.spinnaker.orca.clouddriver.tasks.cache;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.mime.TypedByteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

@Component
public class ClouddriverClearAltTablespaceTask implements Task {
  private final CloudDriverCacheService river;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public ClouddriverClearAltTablespaceTask(CloudDriverCacheService river) {
    this.river = river;
  }

  @NotNull
  @Override
  public TaskResult execute(@NotNull Stage stage) {
    String namespace = ((String) stage.getContext().get("namespace"));
    if (namespace == null) {
      throw new IllegalArgumentException("Missing namespace");
    }

    try {
      Map<String, Object> result = river.clearNamespace(namespace);
      log.info(
        "Cleared clouddriver namespace {}, tables truncated: {}",
        namespace,
        result.getOrDefault("tables", emptyList())
      );

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(result).build();
    } catch (RetrofitError e) {
      Map<String, Object> output = new HashMap<>();
      List<String> errors = new ArrayList<>();

      if (e.getResponse() != null && e.getResponse().getBody() != null) {
        String error = new String(((TypedByteArray) e.getResponse().getBody()).getBytes());
        log.error("Failed clearing clouddriver table namespace: {}", error, e);
        errors.add(error);
      } else {
        errors.add(e.getMessage());
      }
      output.put("errors", errors);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(output).build();
    }
  }
}
