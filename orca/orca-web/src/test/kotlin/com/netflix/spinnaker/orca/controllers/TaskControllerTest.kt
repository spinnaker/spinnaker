/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.TaskControllerConfigurationProperties
import com.netflix.spinnaker.config.TaskControllerConfigurationProperties.FailedStagesProperties
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.STOPPED
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.nhaarman.mockito_kotlin.mock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito

class TaskControllerTest : JUnit5Minutests {
    data class Fixture(val runtests: Boolean) {

        private val front50Service: Front50Service = mock()
        private val executionMockRepository: ExecutionRepository = mock()
        val taskControllerConfigurationProperties: TaskControllerConfigurationProperties = mock()
        val failedStagesProperties: FailedStagesProperties = mock()

        private val taskController: TaskController = TaskController(
            front50Service,
            executionMockRepository,
            mock(),
            mock(),
            listOf(mock()),
            ContextParameterProcessor(),
            mock(),
            OrcaObjectMapper.getInstance(),
            NoopRegistry(),
            mock(),
            taskControllerConfigurationProperties
        )

        init {
          Mockito.`when`(taskControllerConfigurationProperties.failedStages).thenReturn(failedStagesProperties)
        }

        private fun createPipelineExecution(pipelineID: String, stageIds: List<String>, stageTypes: List<String>, stageStatuses: List<ExecutionStatus>, stageContext: List<Map<String, Any>>): PipelineExecutionImpl {

            val pipelineExecution = PipelineExecutionImpl(PIPELINE, "test-app")
            pipelineExecution.id = pipelineID

            for (index in stageIds.indices) {
                val stage1 = StageExecutionImpl(pipelineExecution, stageTypes[index])
                stage1.refId = stageIds[index]
                stage1.id = stageIds[index]
                stage1.status = stageStatuses[index]
                stage1.context = stageContext[index]
                pipelineExecution.stages.add(stage1)
            }

            return pipelineExecution
        }

        fun setupExecutionsInDb(pipelineID: String, stageIds: List<String>, stageTypes: List<String>, stageStatuses: List<ExecutionStatus>, stageContext: List<Map<String, Any>>) {
          Mockito.`when`(executionMockRepository.retrieve(PIPELINE, pipelineID)).thenReturn(
            createPipelineExecution(
              pipelineID,
              stageIds,
              stageTypes,
              stageStatuses,
              stageContext
            )
          )
        }

        fun verifyFailedStagesAPI(rootPipeline: String, limit: Int, expectedOutput: List<String>) {
            val failedStages =
                taskController.getFailedStagesForPipelineExecution(rootPipeline, "", limit)

            assertThat(failedStages.size).isEqualTo(expectedOutput.size)
            assertThat(failedStages.map { it.stageId }).containsExactlyInAnyOrderElementsOf(expectedOutput)
        }
    }

    fun tests() = rootContext<Fixture> {

        context("test failedStages API") {
            fixture {
                Fixture(true)
            }

            test("TestCase1: returns empty list when pipeline not found") {
                verifyFailedStagesAPI(
                    "T1P", 1, listOf()
                )
            }

            test("TestCase2: returns empty list when root pipeline has no failed stages") {
                setupExecutionsInDb(
                  "T2P",
                  listOf("T2PS1", "T2PS2"),
                  listOf("test", "test"),
                  listOf(SUCCEEDED, SUCCEEDED),
                  listOf(mapOf(), mapOf())
                )
                verifyFailedStagesAPI(
                    "T2P", 1, listOf()
                )
            }

            test("TestCase3: returns one o/f one failed leaf stage with limit 2") {
                setupExecutionsInDb(
                  "T3P",
                  listOf("T3PS1", "T3PS2"),
                  listOf("test", "test"),
                  listOf(SUCCEEDED, TERMINAL),
                  listOf(mapOf(), mapOf())
                )

                verifyFailedStagesAPI(
                    "T3P", 2, listOf("T3PS2")
                )
            }

            test("TestCase4a: returns one o/f two failed leaf stage with limit 1") {
                setupExecutionsInDb(
                  "T4P",
                  listOf("T4PS1", "T4PS2"),
                  listOf("test", "test"),
                  listOf(TERMINAL, TERMINAL),
                  listOf(mapOf(), mapOf())
                )

                verifyFailedStagesAPI(
                    "T4P", 1, listOf("T4PS1")
                )
            }

            test("TestCase4b: returns two o/f two failed leaf stages with limit 2") {
                setupExecutionsInDb(
                  "T4P",
                  listOf("T4PS1", "T4PS2"),
                  listOf("test", "test"),
                  listOf(TERMINAL, TERMINAL),
                  listOf(mapOf(), mapOf())
                )

                verifyFailedStagesAPI(
                    "T4P", 2, listOf("T4PS1", "T4PS2")
                )
            }

            test("TestCase5: returns all failed leaf stages in nested pipeline with limit 5") {
                setupExecutionsInDb(
                  "T5P",
                  listOf("T5PS1", "T5PS2", "T5PS3"),
                  listOf("test", "pipeline", "pipeline"),
                  listOf(TERMINAL, SUCCEEDED, TERMINAL),
                  listOf(
                    mapOf(),
                    mapOf("executionId" to "T5PS2P", "application" to "test-app"),
                    mapOf("executionId" to "T5PS3P", "application" to "test-app")
                  )
                )

                setupExecutionsInDb(
                  "T5PS2P",
                  listOf("T5PS2PS1"),
                  listOf("test"),
                  listOf(SUCCEEDED),
                  listOf(mapOf())
                )

                setupExecutionsInDb(
                  "T5PS3P",
                  listOf("T5PS3PS1", "T5PS3PS2"),
                  listOf("pipeline", "test"),
                  listOf(TERMINAL, TERMINAL),
                  listOf(
                    mapOf("executionId" to "T5PS3PS1P", "application" to "test-app"), mapOf()
                  )
                )

                setupExecutionsInDb(
                  "T5PS3PS1P",
                  listOf("T5PS3PS1PS1"),
                  listOf("test"),
                  listOf(TERMINAL),
                  listOf(mapOf())
                )

                verifyFailedStagesAPI(
                    "T5P", 5, listOf("T5PS1", "T5PS3PS1PS1", "T5PS3PS2")
                )
            }

            listOf(true, false).forEach { onlyIncludeStagesThatFailedPipelines ->
              test("TestCase6: When isOnlyIncludeStagesThatFailPipelines is $onlyIncludeStagesThatFailedPipelines, return proper stages") {
                Mockito.`when`(failedStagesProperties.isOnlyIncludeStagesThatFailPipelines).thenReturn(onlyIncludeStagesThatFailedPipelines)
                setupExecutionsInDb(
                  "Test6",
                  listOf(
                    "Test6Stage1Terminal",
                    "Test6Stage2StoppedCompleteOtherBranches",
                    "Test6Stage3StoppedDontCompleteOtherBranches",
                    "Test6Stage4FailedContinue"),
                  listOf("test", "test", "test", "test"),
                  listOf(TERMINAL, STOPPED, STOPPED, FAILED_CONTINUE),
                  listOf(
                    mapOf(),
                    mapOf("completeOtherBranchesThenFail" to true),
                    mapOf("completeOtherBranchesThenFail" to false),
                    mapOf()
                  )
                )

                if (onlyIncludeStagesThatFailedPipelines) {
                  // don't include a status of STOPPED where completeOtherBranchesThenFail is true
                  // or a FAILED_CONTINUE status
                  verifyFailedStagesAPI(
                    "Test6", 5, listOf("Test6Stage1Terminal", "Test6Stage2StoppedCompleteOtherBranches")
                  )
                } else {
                  verifyFailedStagesAPI(
                    "Test6", 5, listOf(
                      "Test6Stage1Terminal",
                      "Test6Stage2StoppedCompleteOtherBranches",
                      "Test6Stage3StoppedDontCompleteOtherBranches",
                      "Test6Stage4FailedContinue")
                  )
                }

              }
            }


        }
    }
}
