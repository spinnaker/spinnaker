package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.CANCELLED
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.COMPLETED
import static org.springframework.batch.repeat.RepeatStatus.CONTINUABLE
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
class MonitorBakeTask implements Tasklet {

    @Autowired
    BakeryService bakery

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def region = chunkContext.stepContext.jobParameters.region as String
        def previousStatus = chunkContext.stepContext.stepExecution.jobExecution.executionContext.with {
            get("bake.status") as BakeStatus
        }
        def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlockingObservable().single()
        chunkContext.stepContext.stepExecution.jobExecution.executionContext.with {
            put("bake.status", newStatus)
        }
        return newStatus.state in [COMPLETED, CANCELLED] ? FINISHED : CONTINUABLE
    }
}
