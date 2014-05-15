package com.netflix.asgard.orca.bakery.tasks

import com.netflix.asgard.orca.bakery.api.BakeService
import groovy.transform.CompileStatic
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired

import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
class BakeTask implements Tasklet {

    @Autowired
    BakeService bakeService

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        bakeService.createBake()
        return FINISHED
    }
}
