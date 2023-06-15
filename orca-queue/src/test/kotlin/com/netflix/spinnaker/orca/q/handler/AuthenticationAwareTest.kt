package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.DefaultStageResolver
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution.LastModifiedDetails
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.*

class AuthenticationAwareTest : SubjectSpek<AuthenticationAware> ({
    val sdbProvider : ObjectProvider<Collection<StageDefinitionBuilder>> = mock()
    whenever(sdbProvider.getIfAvailable(any())).thenReturn(listOf(
        ManualJudgmentStage(),
        WaitStage()
    ))
    val stageNavigator = StageNavigator(DefaultStageResolver(sdbProvider))

    subject(CachingMode.GROUP) {
        object : AuthenticationAware {
            override val stageNavigator: StageNavigator
                get() = stageNavigator
        }
    }

    // Arg defines which named stages get which propagation setting:
    // {
    //   mj1: true,
    //   mj2: false,
    //   mj3: null,
    //   ...
    // }
    fun makePipeline(propagateConfig: Map<String, Boolean?>) : PipelineExecution {
        // Set up a base context to be copied around
        val ctx : MutableMap<String, Any> = mutableMapOf()
        ctx["judgmentStatus"] = "continue"

        // Creates a pipeline that looks like this
        // MJ 1 - Wait 1 - MJ 2 - Wait 2
        //   \ MJ 3 - Wait 3 - MJ 4 (skipped manually) - Wait 4
        //
        // Covering the following important cases:
        // - A root stage being MJ
        // - Multiple MJs in a row
        // - Propagating or not
        // - Different users approving different MJ stages
        //
        // All MJ stages are approved by "propagateUserX" where X is the MJ stage number above
        // Except for MJ 4, which is skipped manually and always has no lastModifiedDetails
        return pipeline {
            stage {
                refId = "1"
                name = "mj1"
                type = "manualJudgment"
                status = ExecutionStatus.SUCCEEDED
                context = ctx.toMutableMap().also {
                    it["lastModifiedBy"] = "propagatedUser1"
                    if(propagateConfig["mj1"] != null) {
                        it["propagateAuthenticationContext"] = propagateConfig["mj1"]!!
                    }
                }
                lastModified = LastModifiedDetails().also {
                    it.user = "propagatedUser1"
                    it.lastModifiedTime = Instant.now().toEpochMilli()
                }
            }
            stage {
                refId = "2"
                name = "wait1"
                type = "wait"
                status = ExecutionStatus.SUCCEEDED
                requisiteStageRefIds = listOf("1")
            }
            stage {
                refId = "3"
                name = "mj2"
                type = "manualJudgment"
                status = ExecutionStatus.SUCCEEDED
                requisiteStageRefIds = listOf("2")
                context = ctx.toMutableMap().also {
                    it["lastModifiedBy"] = "propagatedUser2"
                    if(propagateConfig["mj2"] != null) {
                        it["propagateAuthenticationContext"] = propagateConfig["mj2"]!!
                    }
                }
                lastModified = LastModifiedDetails().also {
                    it.user = "propagatedUser2"
                    it.lastModifiedTime = Instant.now().toEpochMilli()
                }
            }
            stage {
                refId = "4"
                name = "wait2"
                type = "wait"
                requisiteStageRefIds = listOf("3")
            }
            stage {
                refId = "5"
                name = "mj3"
                type = "manualJudgment"
                status = ExecutionStatus.SUCCEEDED
                requisiteStageRefIds = listOf("1")
                context = ctx.toMutableMap().also {
                    it["lastModifiedBy"] = "propagatedUser3"
                    if(propagateConfig["mj3"] != null) {
                        it["propagateAuthenticationContext"] = propagateConfig["mj3"]!!
                    }
                }
                lastModified = LastModifiedDetails().also {
                    it.user = "propagatedUser3"
                    it.lastModifiedTime = Instant.now().toEpochMilli()
                }
            }
            stage {
                refId = "6"
                name = "wait3"
                type = "wait"
                status = ExecutionStatus.SUCCEEDED
                requisiteStageRefIds = listOf("5")
            }
            stage {
                refId = "7"
                name = "mj4"
                type = "manualJudgment"
                status = ExecutionStatus.SKIPPED
                requisiteStageRefIds = listOf("6")
                context = ctx.toMutableMap().also {
                    ctx["manualSkip"] = true
                }
                lastModified = null
            }
            stage {
                refId = "8"
                name = "wait4"
                type = "wait"
                status = ExecutionStatus.SUCCEEDED
                requisiteStageRefIds = listOf("7")
            }
        }
    }

    describe("Propagates authentication from manual judgment stages if configured") {
        it("Finds the propagated user in wait1 after root mj1") {
            val mjp = makePipeline(mapOf("mj1" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("2"))
            assertEquals("propagatedUser1", stageUser?.user)
        }

        it("Finds no propagated user in wait1 after root mj1") {
            val mjp = makePipeline(mapOf("mj1" to false))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("2"))
            assertEquals(null, stageUser?.user, )
        }

        it("Finds no propagated user in wait1 after root mj1 when unset") {
            val mjp = makePipeline(mapOf("mj1" to null))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("2"))
            assertEquals(null, stageUser?.user)
        }

        it("Finds propagated user in wait2 when mj1 & mj2 are set") {
            val mjp = makePipeline(mapOf("mj1" to true, "mj2" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("4"))
            assertEquals("propagatedUser2", stageUser?.user)
        }

        it("Finds propagated user in wait2 when mj1 only is set") {
            val mjp = makePipeline(mapOf("mj1" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("4"))
            assertEquals("propagatedUser1", stageUser?.user)
        }

        it("Finds propagated user in wait3 when mj1 & mj3 are set") {
            val mjp = makePipeline(mapOf("mj1" to true, "mj3" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("6"))
            assertEquals("propagatedUser3", stageUser?.user)
        }

        it("Finds propagated user in wait3 when mj1 only is set") {
            val mjp = makePipeline(mapOf("mj1" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("6"))
            assertEquals("propagatedUser1", stageUser?.user)
        }

        it("Finds propagated user in wait4 when mj1 & mj3 are set") {
            val mjp = makePipeline(mapOf("mj1" to true, "mj3" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("8"))
            assertEquals("propagatedUser3", stageUser?.user)
        }

        it("Finds propagated user in wait4 when mj1 only is set") {
            val mjp = makePipeline(mapOf("mj1" to true))
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("8"))
            assertEquals("propagatedUser1", stageUser?.user)
        }

        it("Finds no propagated user in wait4 when none are set") {
            val mjp = makePipeline(mapOf())
            val stageUser = subject.retrieveAuthenticatedUser(mjp.stageByRef("8"))
            assertEquals(null, stageUser?.user)
        }
    }
})
