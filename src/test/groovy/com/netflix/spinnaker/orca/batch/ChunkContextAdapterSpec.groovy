package com.netflix.spinnaker.orca.batch

import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.test.MetaDataInstanceFactory
import spock.lang.Specification
import spock.lang.Unroll

class ChunkContextAdapterSpec extends Specification {

    def stepExecution = MetaDataInstanceFactory.createStepExecution()
    def stepContext = new StepContext(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def stepExecutionContext = stepExecution.executionContext
    def jobExecutionContext = stepExecution.jobExecution.executionContext

    @Unroll
    def "traverses the heirarchy of contexts to retrieve values"() {
        given:
        if (stepValue) stepExecutionContext.put(key, stepValue)
        if (jobValue) jobExecutionContext.put(key, jobValue)

        and:
        def context = new ChunkContextAdapter(chunkContext)

        expect:
        context.inputs[key] == expected

        where:
        stepValue | jobValue || expected
        "step"    | null     || "step"
        null      | "job"    || "job"
        null      | null     || null
        "step"    | "job"    || "step"

        and:
        key = "foo"
    }

}
