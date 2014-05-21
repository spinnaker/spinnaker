package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

import static com.netflix.spinnaker.orca.TaskResult.*

@CompileStatic
class TaskAdapterTasklet implements Tasklet {

    private final Task step

    TaskAdapterTasklet(Task step) {
        this.step = step
    }

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def result = step.execute()

        def repeatStatus
        switch (result) {
            case SUCCEEDED:
                contribution.exitStatus = ExitStatus.COMPLETED
                repeatStatus = RepeatStatus.FINISHED
                break
            case FAILED:
                contribution.exitStatus = ExitStatus.FAILED
                repeatStatus = RepeatStatus.FINISHED
                break
            case RUNNING:
                contribution.exitStatus = ExitStatus.EXECUTING
                repeatStatus = RepeatStatus.CONTINUABLE
                break
        }
        return repeatStatus
    }
}
