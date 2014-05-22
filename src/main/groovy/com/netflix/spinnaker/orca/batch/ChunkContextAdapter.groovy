package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.TaskContext
import groovy.transform.CompileStatic
import org.springframework.batch.core.scope.context.ChunkContext

@CompileStatic
class ChunkContextAdapter implements TaskContext {

    private final ChunkContext chunkContext

    ChunkContextAdapter(ChunkContext chunkContext) {
        this.chunkContext = chunkContext
    }

    @Override
    def <T> T getAt(String key) {
        chunkContext.stepContext.stepExecution.executionContext.get(key)
    }
}
