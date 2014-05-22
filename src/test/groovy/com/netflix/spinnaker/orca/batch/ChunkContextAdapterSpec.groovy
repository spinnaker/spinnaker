package com.netflix.spinnaker.orca.batch

import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.test.MetaDataInstanceFactory
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ChunkContextAdapterSpec extends Specification {

    def stepExecution = MetaDataInstanceFactory.createStepExecution()
    def stepContext = new StepContext(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def stepExecutionContext = stepExecution.executionContext
    def jobExecutionContext = stepExecution.jobExecution.executionContext

    @Subject
    def context = new ChunkContextAdapter(chunkContext)

    @Unroll
    def "traverses the heirarchy of contexts to retrieve values"() {
        given:
        if (stepValue) stepExecutionContext.put(key, stepValue)
        if (jobValue) jobExecutionContext.put(key, jobValue)

        expect:
        context[key] == expected

        where:
        stepValue | jobValue || expected
        "step"    | null     || "step"
        null      | "job"    || "job"
        null      | null     || null
        "step"    | "job"    || "step"

        and:
        key = "foo"
    }

    @Unroll
    def "traverses the heirarchy of contexts to determine if key is present"() {
        given:
        if (stepValue) stepExecutionContext.put(key, stepValue)
        if (jobValue) jobExecutionContext.put(key, jobValue)

        expect:
        context.containsKey(key) == expected

        where:
        stepValue | jobValue || expected
        "step"    | null     || true
        null      | "job"    || true
        null      | null     || false
        "step"    | "job"    || true

        and:
        key = "foo"
    }

}
