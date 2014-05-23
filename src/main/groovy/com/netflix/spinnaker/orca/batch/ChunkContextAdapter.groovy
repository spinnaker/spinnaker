package com.netflix.spinnaker.orca.batch

import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.TaskContext
import groovy.transform.CompileStatic
import org.springframework.batch.core.scope.context.ChunkContext

@CompileStatic
class ChunkContextAdapter implements TaskContext {

    final Map<String, Object> inputs

    ChunkContextAdapter(ChunkContext chunkContext) {
        def entries = [:]
        entries.putAll(chunkContext.stepContext.jobExecutionContext)
        entries.putAll(chunkContext.stepContext.stepExecutionContext)
        inputs = new ImmutableMap.Builder()
            .putAll(entries)
            .build()
    }
}
