package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.listener.ExecutionContextPromotionListener
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.retry.annotation.Retryable

@CompileStatic
@Retryable
class TaskTaskletAdapter implements Tasklet {

    private final Task task

    TaskTaskletAdapter(Task task) {
        this.task = task
    }

    static Tasklet decorate(Task task) {
        new TaskTaskletAdapter(task)
    }

    Class<? extends Task> getTaskType() {
        task.getClass()
    }

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
        def stepExecutionContext = chunkContext.stepContext.stepExecution.executionContext

        def result = task.execute(new ChunkContextAdapter(chunkContext))

        // TODO: could consider extending ExecutionContextPromotionListener in order to do this but then we need to know exactly which keys to promote
        def executionContext = result.status.complete ? jobExecutionContext : stepExecutionContext
        result.outputs.each { k, v ->
            executionContext.put(k, v)
        }

        def batchStepStatus = BatchStepStatus.mapResult(result)
        contribution.exitStatus = batchStepStatus.exitStatus
        return batchStepStatus.repeatStatus
    }
}

