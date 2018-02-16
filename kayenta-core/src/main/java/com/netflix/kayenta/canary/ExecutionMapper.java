package com.netflix.kayenta.canary;

import com.netflix.kayenta.canary.orca.CanaryStageNames;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExecutionMapper {

  private final StorageServiceRepository storageServiceRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  public ExecutionMapper(StorageServiceRepository storageServiceRepository, AccountCredentialsRepository accountCredentialsRepository) {
    this.storageServiceRepository = storageServiceRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  public CanaryExecutionStatusResponse fromExecution(Execution pipeline) {
    String canaryExecutionId = pipeline.getId();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    String storageAccountName = (String)contextContext.get("storageAccountName");
    return fromExecution(storageAccountName, pipeline);
  }

  public CanaryExecutionStatusResponse fromExecution(String unresolvedStorageAccountName, Execution pipeline) {
    String storageAccountName = CredentialsHelper.resolveAccountByNameOrType(unresolvedStorageAccountName,
                                                                             AccountCredentials.Type.OBJECT_STORE,
                                                                             accountCredentialsRepository);

    StorageService storageService =
      storageServiceRepository
        .getOne(storageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to retrieve results."));

    String canaryExecutionId = pipeline.getId();

    Stage judgeStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_JUDGE))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_JUDGE + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> judgeOutputs = judgeStage.getOutputs();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    Stage mixerStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_MIX_METRICS))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_MIX_METRICS + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> mixerContext = mixerStage.getContext();

    CanaryExecutionStatusResponse.CanaryExecutionStatusResponseBuilder canaryExecutionStatusResponseBuilder =
      CanaryExecutionStatusResponse.builder()
        .application((String)contextContext.get("application"))
        .parentPipelineExecutionId((String)contextContext.get("parentPipelineExecutionId"));

    Map<String, String> stageStatus = pipeline.getStages()
      .stream()
      .collect(Collectors.toMap(Stage::getRefId, s -> s.getStatus().toString().toLowerCase()));

    Boolean isComplete = pipeline.getStatus().isComplete();
    String pipelineStatus = pipeline.getStatus().toString().toLowerCase();

    canaryExecutionStatusResponseBuilder
      .stageStatus(stageStatus)
      .complete(isComplete)
      .status(pipelineStatus);

    Long buildTime = pipeline.getBuildTime();
    if (buildTime != null) {
      canaryExecutionStatusResponseBuilder
        .buildTimeMillis(buildTime)
        .buildTimeIso(Instant.ofEpochMilli(buildTime) + "");
    }

    Long startTime = pipeline.getStartTime();
    if (startTime != null) {
      canaryExecutionStatusResponseBuilder
        .startTimeMillis(startTime)
        .startTimeIso(Instant.ofEpochMilli(startTime) + "");
    }

    Long endTime = pipeline.getEndTime();
    if (endTime != null) {
      canaryExecutionStatusResponseBuilder
        .endTimeMillis(endTime)
        .endTimeIso(Instant.ofEpochMilli(endTime) + "");
    }

    if (isComplete && pipelineStatus.equals("succeeded")) {
      if (judgeOutputs.containsKey("canaryJudgeResultId")) {
        String canaryJudgeResultId = (String)judgeOutputs.get("canaryJudgeResultId");
        canaryExecutionStatusResponseBuilder.result(storageService.loadObject(storageAccountName, ObjectType.CANARY_RESULT, canaryJudgeResultId));
      }
    }

    // Propagate the first canary pipeline exception we can locate.
    Stage stageWithException = pipeline.getStages().stream()
      .filter(stage -> stage.getContext().containsKey("exception"))
      .findFirst()
      .orElse(null);

    if (stageWithException != null) {
      canaryExecutionStatusResponseBuilder.exception(stageWithException.getContext().get("exception"));
    }

    canaryExecutionStatusResponseBuilder.storageAccountName(storageAccountName);

    return canaryExecutionStatusResponseBuilder.build();
  }

}
