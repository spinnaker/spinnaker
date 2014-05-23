package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import groovy.transform.CompileStatic
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

@CompileStatic
class TaskAdapterTasklet implements Tasklet {

    private final Task step

    TaskAdapterTasklet(Task step) {
        this.step = step
    }

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
        def stepExecutionContext = chunkContext.stepContext.stepExecution.executionContext

        def result = step.execute(new ChunkContextAdapter(chunkContext))

        def executionContext = result.status.complete ? jobExecutionContext : stepExecutionContext
        result.outputs.each { k, v ->
            executionContext.put(k, v)
        }

        def batchStepStatus = BatchStepStatus.forTaskResult(result)
        contribution.exitStatus = batchStepStatus.exitStatus
        return batchStepStatus.repeatStatus
    }

}

