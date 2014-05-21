package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.Bake.Label
import com.netflix.spinnaker.orca.bakery.api.Bake.OperatingSystem
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired

import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
class CreateBakeTask implements Tasklet {

    @Autowired
    BakeryService bakery

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def region = chunkContext.stepContext.jobParameters.region as String
        def bake = bakeFromContext(chunkContext)

        def bakeStatus = bakery.createBake(region, bake).toBlockingObservable().single()
        chunkContext.stepContext.stepExecution.jobExecution.executionContext.with {
            put("bake.status", bakeStatus)
        }
        return FINISHED
    }

    private Bake bakeFromContext(ChunkContext chunkContext) {
        def jobContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
        // TODO: use a Groovy 2.3 @Builder
        new Bake(jobContext.getString("bake.user"),
            jobContext.getString("bake.package"),
            Label.valueOf(jobContext.getString("bake.baseLabel")),
            OperatingSystem.valueOf(jobContext.getString("bake.baseOs"))
        )
    }
}
