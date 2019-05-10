package com.netflix.spinnaker.orca.controllers;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class CorrelatedTasksController {

  private final ExecutionRepository executionRepository;

  @Autowired
  public CorrelatedTasksController(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @GetMapping(
    path = "/executions/correlated/{correlationId}",
    produces = APPLICATION_JSON_VALUE
  )
  public List<String> getCorrelatedExecutions(@PathVariable String correlationId) {
    return Stream
      .<Execution>builder()
      .add(getCorrelated(PIPELINE, correlationId))
      .add(getCorrelated(ORCHESTRATION, correlationId))
      .build()
      .filter(Objects::nonNull)
      .map(Execution::getId)
      .collect(toList());
  }

  private Execution getCorrelated(ExecutionType executionType, String correlationId) {
    try {
      return executionRepository.retrieveByCorrelationId(executionType, correlationId);
    } catch (ExecutionNotFoundException ignored) {
      return null;
    }
  }
}
