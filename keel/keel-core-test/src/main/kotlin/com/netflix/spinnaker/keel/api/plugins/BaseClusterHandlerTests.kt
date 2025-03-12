package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Job
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.serverGroup
import com.netflix.spinnaker.time.MutableClock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEmpty
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.time.Clock

abstract class BaseClusterHandlerTests<
  SPEC: ComputeResourceSpec<*>, // spec type
  RESOLVED: Any, // resolved type
  HANDLER : BaseClusterHandler<SPEC, RESOLVED>
  > {

  abstract fun createSpyHandler(
    resolvers: List<Resolver<*>>,
    clock: Clock,
    eventPublisher: EventPublisher,
    taskLauncher: TaskLauncher,
  ): HANDLER

  abstract fun getSingleRegionCluster(): Resource<SPEC>
  abstract fun getMultiRegionCluster(): Resource<SPEC>
  abstract fun getMultiRegionStaggeredDeployCluster(): Resource<SPEC>
  abstract fun getMultiRegionManagedRolloutCluster(): Resource<SPEC>

  abstract fun getRegions(resource: Resource<SPEC>): List<String>
  abstract fun getDiffInMoreThanEnabled(resource: Resource<SPEC>): ResourceDiff<Map<String, RESOLVED>>

  abstract fun getDiffOnlyInEnabled(resource: Resource<SPEC>): ResourceDiff<Map<String, RESOLVED>>
  abstract fun getDiffInCapacity(resource: Resource<SPEC>): ResourceDiff<Map<String, RESOLVED>>
  abstract fun getDiffInImage(resource: Resource<SPEC>, version: String? = null): ResourceDiff<Map<String, RESOLVED>>
  abstract fun getCreateAndModifyDiff(resource: Resource<SPEC>): ResourceDiff<Map<String, RESOLVED>>
  abstract fun getDiffForRollback(resource: Resource<SPEC>, version: String, currentMoniker: Moniker): ResourceDiff<Map<String, RESOLVED>>
  abstract fun getDiffForRollbackPlusCapacity(resource: Resource<SPEC>, version: String, currentMoniker: Moniker): ResourceDiff<Map<String, RESOLVED>>

  abstract fun getRollbackServerGroupsByRegion(resource: Resource<SPEC>, version: String, rollbackMoniker: Moniker): Map<String, List<RESOLVED>>
  abstract fun getRollbackServerGroupsByRegionZeroCapacity(resource: Resource<SPEC>, version: String, rollbackMoniker: Moniker): Map<String, List<RESOLVED>>

  val clock: Clock = MutableClock()
  val eventPublisher: EventPublisher = mockk(relaxUnitFun = true)
  val resolvers: List<Resolver<*>> = emptyList()
  val taskLauncher: TaskLauncher = mockk()

  data class Fixture<SPEC: ComputeResourceSpec<*>, RESOLVED: Any, HANDLER : BaseClusterHandler<SPEC, RESOLVED>>(
    val handler: HANDLER
  )

  val handler by lazy {
    // we create a spy handler so that we can override the results of functions
    // without having to set up every little bit of cloud specific data
    createSpyHandler(
      resolvers = resolvers,
      clock = clock,
      eventPublisher = eventPublisher,
      taskLauncher = taskLauncher,
    )
  }

  @Test
  fun `handler will take action if diff is in more than enabled`() {
    val resource = getSingleRegionCluster()
    val diff = getDiffInMoreThanEnabled(resource)
    val response = runBlocking { handler.willTakeAction(resource, diff) }
    expectThat(response.willAct).isTrue()
  }

  @Test
  fun `handler will take action if enabled diff and all regions are healthy`() {
    every { handler.getUnhealthyRegionsForActiveServerGroup(any()) } returns emptyList()
    val resource = getSingleRegionCluster()
    val diff = getDiffOnlyInEnabled(resource)
    val response = runBlocking { handler.willTakeAction(resource, diff) }
    expectThat(response.willAct).isTrue()
  }

  @Test
  fun `handler will NOT take action if enabled diff and all regions are NOT healthy`() {
    every { handler.getUnhealthyRegionsForActiveServerGroup(any()) } returns getRegions(getSingleRegionCluster())

    val resource = getSingleRegionCluster()
    val diff = getDiffOnlyInEnabled(resource)
    val response = runBlocking { handler.willTakeAction(resource, diff) }
    expectThat(response.willAct).isFalse()
  }

  @Test
  fun `staggered deploy, multi region, image diff`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>() // done this way so we can capture the stages for multiple requests
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getMultiRegionStaggeredDeployCluster()
    runBlocking { handler.upsert(resource, getDiffInImage(resource)) }

    val firstRegionStages = slots[0]
    val secondRegionStages = slots[1]
    expect {
      // first region
      that(firstRegionStages).isNotEmpty().hasSize(2)
      val deployStage1 = firstRegionStages[0]
      that(deployStage1["type"]).isEqualTo("createServerGroup")
      that(deployStage1["refId"]).isEqualTo("1")
      that(deployStage1["requisiteRefIds"]).isNull()
      val waitStage = firstRegionStages[1]
      that(waitStage["type"]).isEqualTo("wait")
      that(waitStage["refId"]).isEqualTo("2")
      that(waitStage["requisiteStageRefIds"] as? List<*>).isEqualTo(listOf("1"))

      //second region
      that(secondRegionStages).isNotEmpty().hasSize(2)
      val dependsOnExecutionStage = secondRegionStages[0]
      that(dependsOnExecutionStage["type"]).isEqualTo("dependsOnExecution")
      that(dependsOnExecutionStage["refId"]).isEqualTo("1")
      that(dependsOnExecutionStage["requisiteRefIds"]).isNull()
      val deployStage2 = secondRegionStages[1]
      that(deployStage2["type"]).isEqualTo("createServerGroup")
      that(deployStage2["refId"]).isEqualTo("2")
      that(deployStage2["requisiteStageRefIds"] as? List<*>).isEqualTo(listOf("1"))
    }
  }

  @Test
  fun `staggered deploy, multi region, capacity diff (no stagger resize stages)`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>() // done this way so we can capture the stages for multiple requests
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getMultiRegionStaggeredDeployCluster()
    runBlocking { handler.upsert(resource, getDiffInCapacity(resource)) }

    val region1Stages = slots[0]
    val region2Stages = slots[1]
    val stages = slots.associate {
      it[0]["region"] to it[0]
    }

    expect {
      that(region1Stages).isNotEmpty().hasSize(1)
      val resizeEast = stages["east"] as Map<String, Any?>
      that(resizeEast["type"]).isEqualTo("resizeServerGroup")
      that(resizeEast["refId"]).isEqualTo("1")
      that(resizeEast["requisiteRefIds"]).isNull()
      that(resizeEast["region"]).isEqualTo("east")

      that(region2Stages).isNotEmpty().hasSize(1)
      val resizeWest = stages["west"] as Map<String, Any?>
      that(resizeWest["type"]).isEqualTo("resizeServerGroup")
      that(resizeWest["refId"]).isEqualTo("1")
      that(resizeWest["requisiteRefIds"]).isNull()
      that(resizeWest["region"]).isEqualTo("west")
    }
  }

  @Test
  fun `non staggered deploy, multi region, image diff`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>() // done this way so we can capture the stages for multiple requests
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getMultiRegionCluster()
    runBlocking { handler.upsert(resource, getDiffInImage(resource)) }

    val firstRegionStages = slots[0]
    val secondRegionStages = slots[1]
    expect {
      // first region
      that(firstRegionStages).isNotEmpty().hasSize(1)
      val deployStage1 = firstRegionStages[0]
      that(deployStage1["type"]).isEqualTo("createServerGroup")
      that(deployStage1["refId"]).isEqualTo("1")

      //second region
      that(secondRegionStages).isNotEmpty().hasSize(1)
      val deployStage2 = secondRegionStages[0]
      that(deployStage2["type"]).isEqualTo("createServerGroup")
      that(deployStage2["refId"]).isEqualTo("1")
    }
  }

  @Test
  fun `non staggered deploy, one region, capacity diff`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getSingleRegionCluster()
    runBlocking { handler.upsert(resource, getDiffInCapacity(resource)) }
    expect {
      that(slots.size).isEqualTo(1)
      val stages = slots[0]
      that(stages.size).isEqualTo(1)
      that(stages.first()["type"]).isEqualTo("resizeServerGroup")
      that(stages.first()["refId"]).isEqualTo("1")
    }
  }

  @Test
  fun `non staggered deploy, one region, image diff`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getSingleRegionCluster()
    runBlocking { handler.upsert(resource, getDiffInImage(resource)) }
    expect {
      that(slots.size).isEqualTo(1)
      val stages = slots[0]
      that(stages.size).isEqualTo(1)
      that(stages.first()["type"]).isEqualTo("createServerGroup")
      that(stages.first()["refId"]).isEqualTo("1")
    }
  }

  @Test
  fun `managed rollout image diff`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots)) } returns Task("id", "name")

    val resource = getMultiRegionManagedRolloutCluster()
    runBlocking { handler.upsert(resource, getDiffInImage(resource)) }
    val stages = slots[0]
    expect {
      that(slots.size).isEqualTo(1)
      that(stages.size).isEqualTo(1)
      val managedRolloutStage = stages.first()
      that(managedRolloutStage["type"]).isEqualTo("managedRollout")
      that(managedRolloutStage["refId"]).isEqualTo("1")
      that(managedRolloutStage["input"]).isA<Map<String, Any?>>()
    }
  }

  @Test
  fun `managed rollout image diff plus capacity change`() {
    coEvery { handler.getServerGroupsByRegion(any()) } returns emptyMap()

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    val resource = getMultiRegionManagedRolloutCluster()
    runBlocking { handler.upsert(resource, getCreateAndModifyDiff(resource)) }
    val firstTask = slots[0]
    val secondTask = slots[1]
    expect {
      that(slots.size).isEqualTo(2)
      that(firstTask).isNotEmpty().hasSize(1)
      that(secondTask).isNotEmpty().hasSize(1)
      val modifyStage = firstTask.first()
      that(modifyStage["type"]).isEqualTo("resizeServerGroup")
      that(modifyStage["refId"]).isEqualTo("1")
      val managedRolloutStage = secondTask.first()
      that(managedRolloutStage["type"]).isEqualTo("managedRollout")
      that(managedRolloutStage["refId"]).isEqualTo("1")
      val targets = (managedRolloutStage["input"] as Map<String, Any?>)["targets"] as List<Map<String,Any?>>
      that(targets).hasSize(1)
    }
  }

  @Test
  fun `will rollback to a given server group`() {
    val resource = getSingleRegionCluster()
    val version = "sha:222"
    val currentMoniker = resource.spec.moniker.copy(sequence = 2)
    val rollbackMoniker = resource.spec.moniker.copy(sequence = 1)
    coEvery { handler.getServerGroupsByRegion(resource) } returns
      getRollbackServerGroupsByRegion(resource, version, rollbackMoniker)

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    runBlocking { handler.upsert(resource, getDiffForRollback(resource, version, currentMoniker)) }

    val stages = slots[0]
    expect {
      that(slots.size).isEqualTo(1)
      that(stages.size).isEqualTo(1)
      val rollbackStage = stages.first()
      that(rollbackStage["type"]).isEqualTo("rollbackServerGroup")
      that(rollbackStage["rollbackContext"]).isA<Map<String, Any?>>()
      val rollbackContext = rollbackStage["rollbackContext"] as Map<String, Any?>
      that(rollbackContext["rollbackServerGroupName"]).isEqualTo(currentMoniker.serverGroup)
      that(rollbackContext["restoreServerGroupName"]).isEqualTo(rollbackMoniker.serverGroup)
    }
  }

  @Test
  fun `rollback to disabled server group with wrong capacity`() {
    // the rollback tasks fixes the capacity
    val resource = getSingleRegionCluster()
    val version = "sha:222"
    val currentMoniker = resource.spec.moniker.copy(sequence = 2)
    val rollbackMoniker = resource.spec.moniker.copy(sequence = 1)
    coEvery { handler.getServerGroupsByRegion(resource) } returns
      getRollbackServerGroupsByRegionZeroCapacity(resource, version, rollbackMoniker)

    val slots = mutableListOf<List<Job>>()
    coEvery { taskLauncher.submitJob(any(), any(), any(), capture(slots), any()) } returns Task("id", "name")

    runBlocking { handler.upsert(resource, getDiffForRollbackPlusCapacity(resource, version, currentMoniker)) }

    val stages = slots[0]
    expect {
      that(slots.size).isEqualTo(1)
      that(stages.size).isEqualTo(1)
      val rollbackStage = stages.first()
      that(rollbackStage["type"]).isEqualTo("rollbackServerGroup")
      that(rollbackStage["rollbackContext"]).isA<Map<String, Any?>>()
      val rollbackContext = rollbackStage["rollbackContext"] as Map<String, Any?>
      that(rollbackContext["rollbackServerGroupName"]).isEqualTo(currentMoniker.serverGroup)
      that(rollbackContext["restoreServerGroupName"]).isEqualTo(rollbackMoniker.serverGroup)
    }
  }
}




