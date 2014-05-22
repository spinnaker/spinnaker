package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.TaskContext
import groovy.transform.CompileStatic
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.item.ExecutionContext

@CompileStatic
class ChunkContextAdapter implements TaskContext {

    private final ChunkContext chunkContext
    private final ExecutionContext stepExecutionContext
    private final ExecutionContext jobExecutionContext

    ChunkContextAdapter(ChunkContext chunkContext) {
        this.chunkContext = chunkContext
        stepExecutionContext = chunkContext.stepContext.stepExecution.executionContext
        jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
    }

    @Override
    def <T> T getAt(String key) {
        if (stepExecutionContext.containsKey(key)) {
            stepExecutionContext.get(key)
        } else {
            jobExecutionContext.get(key)
        }
    }

    @Override
    void putAt(String key, value) {
        stepExecutionContext.put(key, value)
    }
}
