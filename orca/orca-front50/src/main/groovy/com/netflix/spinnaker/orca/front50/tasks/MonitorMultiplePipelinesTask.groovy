/*
 * Copyright 2022 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.front50.multiplepipelines.RunMultiplePipelinesOutputs
import com.netflix.spinnaker.orca.front50.pipeline.MonitorPipelineStage
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.apache.commons.lang3.ObjectUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
/**
 * This started as a copy of {@link MonitorPipelineTask} with these changes:
 * <p> passes this condition (stage.type == MonitorPipelineStage.PIPELINE_CONFIG_TYPE) </p>
 * <p> get and update PipelinesExecutions list to refer to it in outputs </p>
 * <p> Originally contributed as part of the Armory.RunMultiplePipelines plugin
 * (https://github.com/armory-plugins/spinnaker-multiple-pipelines). </p>
 */
@Component
class MonitorMultiplePipelinesTask implements OverridableTimeoutRetryableTask {

    // matches DeployManifestStage.PIPELINE_CONFIG_TYPE in orca-clouddriver, which this module
    // cannot depend on
    private static final String DEPLOY_MANIFEST_STAGE_TYPE = "deployManifest"

    ExecutionRepository executionRepository
    ObjectMapper objectMapper

    long backoffPeriod = TimeUnit.SECONDS.toMillis(15)
    long timeout = TimeUnit.HOURS.toMillis(24)

    private final Logger logger = LoggerFactory.getLogger(MonitorMultiplePipelinesTask.class)

    @Autowired
    MonitorMultiplePipelinesTask(ExecutionRepository executionRepository, ObjectMapper objectMapper) {
        this.executionRepository = executionRepository
        this.objectMapper = objectMapper
    }

    @Override
    TaskResult execute(StageExecution stage) {
        stage.context.putIfAbsent("runMultiplePipelinesOutputs", new LinkedList())
        List<RunMultiplePipelinesOutputs> multiplePipelinesOutputsList =  stage.getContext().get("runMultiplePipelinesOutputs")
        int levelNumber = stage.getContext().get("levelNumber") as int
        int orderOfExecutionsSize = stage.getContext().get("orderOfExecutionsSize") as int
        stage.getContext().put("monitorBehavior", MonitorPipelineStage.MonitorBehavior.WaitForAllToComplete)
        List<String> pipelineIds
        boolean isLegacyStage = false
        MonitorPipelineStage.StageParameters stageData = stage.mapTo(MonitorPipelineStage.StageParameters.class)

        if (stage.type == "runMultiplePipelines") {
            pipelineIds = stageData.executionIds
        } else {
            pipelineIds = Collections.singletonList(stageData.executionId)
            isLegacyStage = true
        }

        HashMap<String, MonitorPipelineStage.ChildPipelineStatusDetails> pipelineStatuses = new HashMap<>(pipelineIds.size())
        List<PipelineExecution> pipelineExecutionsOutput = new ArrayList<>()
        PipelineExecution firstPipeline

        for (String pipelineId : pipelineIds) {
            PipelineExecution childPipeline = executionRepository.retrieve(PIPELINE, pipelineId)
            pipelineExecutionsOutput.add(childPipeline)
            if (firstPipeline == null) {
                // Capture the first pipeline, since if there is only one, we will return its context as part of TaskResult
                firstPipeline = childPipeline
            }
            MonitorPipelineStage.ChildPipelineStatusDetails details = new MonitorPipelineStage.ChildPipelineStatusDetails()
            details.status = childPipeline.status
            details.application = childPipeline.application
            pipelineStatuses.put(pipelineId, details)

            if (childPipeline.status.halt) {
                details.exception = new MonitorPipelineStage.ChildPipelineException()

                // indicates a failure of some sort
                def terminalStages = childPipeline.stages.findAll { s -> s.status == ExecutionStatus.TERMINAL }
                List<String> errors = terminalStages
                        .findResults { s ->
                            if (s.context["exception"]?.details) {
                                return [(s.context["exception"].details.errors ?: s.context["exception"].details.error)]
                                        .flatten()
                                        .collect { e -> buildExceptionMessage(childPipeline.name, e as String, s) }
                            }
                            if (s.context["kato.tasks"]) {
                                return s.context["kato.tasks"]
                                        .findAll { k -> k.status?.failed }
                                        .findResults { k ->
                                            String message = k.exception?.message ?: k.history ? ((List<String>) k.history).last() : null
                                            return message ? buildExceptionMessage(childPipeline.name, message, s) : null
                                        }
                            }
                        }
                        .flatten()

                details.exception.details.errors = errors

                def haltingStage = terminalStages.find { it.status.halt }
                if (haltingStage) {
                    details.exception.source.executionId = childPipeline.id
                    details.exception.source.stageId = haltingStage.id
                    details.exception.source.stageName = haltingStage.name
                    details.exception.source.stageIndex = childPipeline.stages.indexOf(haltingStage)
                }
            }
        }

        boolean allPipelinesSucceeded = pipelineStatuses.every { it.value.status == ExecutionStatus.SUCCEEDED }
        boolean allPipelinesCompleted = pipelineStatuses.every { it.value.status.complete }
        boolean anyPipelinesFailed = pipelineStatuses.any { effectiveStatus(it.value.status) == ExecutionStatus.TERMINAL }

        MonitorPipelineStage.StageResult result = new MonitorPipelineStage.StageResult()
        Map<String, Object> context = new HashMap<>()

        if (isLegacyStage) {
            context.put("status", firstPipeline.status)
            if (anyPipelinesFailed) {
                context.put("exception", objectMapper.convertValue(pipelineStatuses.values().first().exception, Map))
            }
        } else {
            result.executionStatuses = pipelineStatuses
        }
        List<PipelineExecution> pipelineExecutions = pipelineExecutionsOutput

        if (allPipelinesSucceeded) {
            logger.info("All child pipelines SUCCEEDED")
            pipelineExecutions = filterStages(pipelineExecutions)
            processOutputs(pipelineExecutions, multiplePipelinesOutputsList)
            if (levelNumber < (orderOfExecutionsSize-1)) {
                stage.getContext().put("levelNumber", ++levelNumber)
                return TaskResult
                        .builder(ExecutionStatus.REDIRECT)
                        .context(stage.getContext())
                        .build()
            }
            return buildTaskResult(ExecutionStatus.SUCCEEDED, context, result)
        }

        if (anyPipelinesFailed) {
            if (allPipelinesCompleted || stageData.monitorBehavior == MonitorPipelineStage.MonitorBehavior.FailFast) {
                logger.info("Some child pipelines FAILED")
                stage.appendErrorMessage("At least one monitored pipeline failed, look for errors in failed pipelines")
                pipelineExecutions = filterStages(pipelineExecutions)
                processOutputs(pipelineExecutions, multiplePipelinesOutputsList)
                if (stage.getContext().get("ignoreUncompleted")==true && levelNumber < (orderOfExecutionsSize-1)) {
                    stage.getContext().put("levelNumber", ++levelNumber)
                    return TaskResult
                            .builder(ExecutionStatus.REDIRECT)
                            .context(stage.getContext())
                            .build()
                }

                return buildTaskResult(ExecutionStatus.TERMINAL, stage, multiplePipelinesOutputsList)
            }
        }

        // Finally, if all pipelines completed and we didn't catch that case above it means at least one of those pipelines was CANCELED
        // and we should propagate that result up
        if (allPipelinesCompleted) {
            logger.info("Some child pipelines were CANCELED")
            stage.appendErrorMessage("At least one monitored pipeline was cancelled")
            pipelineExecutions = filterStages(pipelineExecutions)
            processOutputs(pipelineExecutions, multiplePipelinesOutputsList)
            if (stage.getContext().get("ignoreUncompleted")==true && levelNumber < (orderOfExecutionsSize-1)) {
                stage.getContext().put("levelNumber", ++levelNumber)
                return TaskResult
                        .builder(ExecutionStatus.REDIRECT)
                        .context(stage.getContext())
                        .build()
            }

            return buildTaskResult(ExecutionStatus.CANCELED, stage, multiplePipelinesOutputsList)
        }

        return buildTaskResult(ExecutionStatus.RUNNING, context, result)
    }

    private List<PipelineExecution> filterStages(List<PipelineExecution> executions) {
        ArrayList<PipelineExecution> filteredStagesExecutions = new ArrayList<>()
        for (PipelineExecution pipelineExecution : executions) {
             List<StageExecution> deployStages = pipelineExecution.getStages().stream()
                     .filter({ stage ->
                         if(stage.type.equals(DEPLOY_MANIFEST_STAGE_TYPE) || stage.type.equals("runJobManifest")) {
                             return true
                         }
                         return false
                     })
                     .filter({ stage -> stage.name.startsWith("Deploy") })
                     .filter({ stage -> stage.status != ExecutionStatus.SKIPPED })
                     .collect(Collectors.toList())

            deployStages = deployStages.stream().filter({ stage ->
                if (ObjectUtils.isNotEmpty(stage.getOutputs())) {
                    List<Map<String, Object>> manifests = objectMapper.readValue(objectMapper.writeValueAsString(
                            stage.getOutputs().getOrDefault("manifests", stage.getOutputs().getOrDefault("outputs.manifests", new ArrayList()))),
                            new TypeReference<List<Map<String, Object>>>() {})

                    if ( !manifests.isEmpty() && manifests.get(0).get("metadata")!=null ) {
                        Map<String, Object> metadata = manifests.get(0).get("metadata")
                        String name = metadata.get("name")
                        String appParam = pipelineExecution.getTrigger().getParameters().get("app") as String
                        if ((manifests.get(0).get("kind").equals("DaemonSet") ||
                                manifests.get(0).get("kind").equals("Deployment") ||
                                manifests.get(0).get("kind").equals("StatefulSet")) &&
                                ObjectUtils.isNotEmpty(appParam) &&
                                name.contains(appParam)) {
                            return true
                        }
                    }
                    return false
                }
                return false
            }).collect(Collectors.toList())
            StageExecution deployFoundStage = deployStages.stream()
                    .reduce({ prev, current ->
                        return prev.endTime > current.endTime ? prev : current
                    }).orElse(null)
            pipelineExecution.getStages().clear()
            deployStages.clear()
            if (ObjectUtils.isNotEmpty(deployFoundStage)) {
                deployStages.add(deployFoundStage)
            }
            pipelineExecution.getStages().addAll(deployStages)
            filteredStagesExecutions.add(pipelineExecution)
        }
        return filteredStagesExecutions
    }

    private void processOutputs(List<PipelineExecution> executions, List<RunMultiplePipelinesOutputs> multiplePipelinesOutputsList) {
        for (PipelineExecution execution : executions) {
            PipelineTrigger trigger = execution.getTrigger()
            Map modifiedTrigger = objectMapper.readValue(objectMapper.writeValueAsString(trigger.parentExecution.trigger), Map.class)
            RunMultiplePipelinesOutputs.ArtifactCreated artifactCreated = null
            if (ObjectUtils.isNotEmpty(execution.getStages())) {
                artifactCreated =  parseArtifact(execution.getStages().get(0))
            }
            RunMultiplePipelinesOutputs pipelineResults = new RunMultiplePipelinesOutputs(
                    id: execution.getId(),
                    executionIdentifier: modifiedTrigger.get("executionIdentifier"),
                    startTime: execution.getStartTime(),
                    endTime: execution.getEndTime(),
                    status: execution.getStatus(),
                    artifactCreated: artifactCreated
            )
            multiplePipelinesOutputsList.add(pipelineResults)
        }
    }

    private RunMultiplePipelinesOutputs.ArtifactCreated parseArtifact(StageExecution deployStage) {
        List<Map<String, Object>> createdArtifacts = (List<Map<String, Object>>) deployStage.getOutputs().get("outputs.createdArtifacts")
        List<Map<String, Object>> manifests = (List<Map<String, Object>>) deployStage.getOutputs().get("manifests")

        if (ObjectUtils.isNotEmpty(createdArtifacts) && ObjectUtils.isNotEmpty(manifests)) {
            String account = deployStage.getContext().get("deploy.account.name")
            String manifestName = manifests.get(0).get("kind") + " " + createdArtifacts.get(0).get("name")
            String location = createdArtifacts.get(0).get("location")
            return new RunMultiplePipelinesOutputs.ArtifactCreated(account: account, manifestName: manifestName, location: location)
        }
        return null
    }

    private buildTaskResult(ExecutionStatus status, Map<String, Object> context, MonitorPipelineStage.StageResult result) {
        return TaskResult.builder(status)
                .context(context)
                .outputs(objectMapper.convertValue(result, Map))
                .build()
    }

    private buildTaskResult(ExecutionStatus status, StageExecution stage, List<RunMultiplePipelinesOutputs> multiplePipelinesOutputsList) {
        stage.getOutputs().clear()
        stage.getContext().remove("orderOfExecutions")
        stage.getContext().remove("runMultiplePipelinesOutputs")
        stage.outputs.put("executionsList", multiplePipelinesOutputsList)
        return TaskResult
                .builder(status)
                .context(stage.getContext())
                .outputs(stage.getOutputs())
                .build()
    }

    private static effectiveStatus(ExecutionStatus status) {
        if (!status.halt) {
            return status
        }

        if (status == ExecutionStatus.CANCELED) {
            return ExecutionStatus.CANCELED
        }

        return ExecutionStatus.TERMINAL
    }

    private static String buildExceptionMessage(String pipelineName, String message, StageExecution stage) {
        "Exception in child pipeline stage (${pipelineName}: ${stage.name ?: stage.type}): ${message}"
    }
}
