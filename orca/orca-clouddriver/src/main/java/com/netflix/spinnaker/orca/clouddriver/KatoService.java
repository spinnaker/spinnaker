package com.netflix.spinnaker.orca.clouddriver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.ExecutionContext;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class KatoService {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KatoRestService katoRestService;
  private final CloudDriverTaskStatusService cloudDriverTaskStatusService;
  private final RetrySupport retrySupport;
  private final ObjectMapper objectMapper;

  public KatoService(
      KatoRestService katoRestService,
      CloudDriverTaskStatusService cloudDriverTaskStatusService,
      RetrySupport retrySupport,
      ObjectMapper objectMapper) {
    this.katoRestService = katoRestService;
    this.cloudDriverTaskStatusService = cloudDriverTaskStatusService;
    this.retrySupport = retrySupport;
    this.objectMapper = objectMapper;
  }

  public TaskId requestOperations(Collection<Map<String, Map>> operations) {
    return retrySupport.retry(
        () ->
            Retrofit2SyncCall.execute(
                katoRestService.requestOperations(requestId(operations), operations)),
        3,
        Duration.ofSeconds(1),
        false);
  }

  public TaskId requestOperations(String cloudProvider, Collection<Map<String, Map>> operations) {
    return retrySupport.retry(
        () ->
            Retrofit2SyncCall.execute(
                katoRestService.requestOperations(
                    requestId(operations), cloudProvider, operations)),
        3,
        Duration.ofSeconds(1),
        false);
  }

  public SubmitOperationResult submitOperation(
      @Nonnull String cloudProvider, OperationContext operation) {
    TaskId taskId;
    try (ResponseBody responseBody =
        Retrofit2SyncCall.execute(
            katoRestService.submitOperation(
                requestId(operation), cloudProvider, operation.getOperationType(), operation))) {

      taskId = objectMapper.readValue(responseBody.byteStream(), TaskId.class);
    } catch (Exception e) {
      throw new IntegrationException("Unable to read response from submitted operation.", e);
    }

    SubmitOperationResult result = new SubmitOperationResult();
    result.setId(taskId.getId());
    result.setStatus(HttpStatus.OK.value());

    return result;
  }

  public Task lookupTask(String id, boolean skipReplica) {
    if (skipReplica) {
      return Retrofit2SyncCall.execute(katoRestService.lookupTask(id));
    }

    return Retrofit2SyncCall.execute(cloudDriverTaskStatusService.lookupTask(id));
  }

  @Nonnull
  public TaskId resumeTask(@Nonnull String id) {
    return Retrofit2SyncCall.execute(katoRestService.resumeTask(id));
  }

  public TaskOwner lookupTaskOwner(@Nonnull String cloudProvider, String id) {
    return Retrofit2SyncCall.execute(
        cloudDriverTaskStatusService.lookupTaskOwner(cloudProvider, id));
  }

  public TaskId updateTaskRetryability(@Nonnull String cloudProvider, String id, boolean retry) {
    return Retrofit2SyncCall.execute(
        katoRestService.updateTask(cloudProvider, id, Map.of("retry", retry)));
  }

  @Nonnull
  public TaskId restartTask(
      @Nonnull String cloudProvider, @Nonnull String id, Collection<Map<String, Map>> operations) {
    return Retrofit2SyncCall.execute(
        katoRestService.restartTaskViaOperations(cloudProvider, id, operations));
  }

  private String requestId(Object payload) {
    final ExecutionContext context = ExecutionContext.get();
    byte[] payloadBytes = new byte[0];
    try {
      payloadBytes = OrcaObjectMapper.getInstance().writeValueAsBytes(payload);
    } catch (Exception e) {
      log.warn(
          "Unable to convert payload '{}' to byte array while computing request operation Id",
          payload);
    }

    return Hashing.sha256()
        .hashBytes(
            String.format(
                    "%s-%s-%s",
                    context.getStageId(),
                    context.getStageStartTime(),
                    Arrays.toString(payloadBytes))
                .getBytes())
        .toString();
  }
}
