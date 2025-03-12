package com.netflix.spinnaker.orca.clouddriver.tasks.cache;

import static java.util.Collections.emptyList;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClouddriverClearAltTablespaceTask implements Task {
  private final CloudDriverCacheService river;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public ClouddriverClearAltTablespaceTask(CloudDriverCacheService river) {
    this.river = river;
  }

  @Nonnull
  @NotNull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String namespace = ((String) stage.getContext().get("namespace"));
    if (namespace == null) {
      throw new IllegalArgumentException("Missing namespace");
    }

    try {
      Map<String, Object> result = river.clearNamespace(namespace);
      log.info(
          "Cleared clouddriver namespace {}, tables truncated: {}",
          namespace,
          result.getOrDefault("tables", emptyList()));

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(result).build();
    } catch (SpinnakerServerException e) {
      Map<String, Object> output = new HashMap<>();
      List<String> errors = new ArrayList<>();

      errors.add(e.getMessage());
      output.put("errors", errors);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(output).build();
    }
  }
}
