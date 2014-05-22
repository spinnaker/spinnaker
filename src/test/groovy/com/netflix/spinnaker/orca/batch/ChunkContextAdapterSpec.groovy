package com.netflix.spinnaker.orca.batch

import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.test.MetaDataInstanceFactory
import spock.lang.Specification
import spock.lang.Subject

class ChunkContextAdapterSpec extends Specification {

    def stepExecution = MetaDataInstanceFactory.createStepExecution()
    def stepContext = new StepContext(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def stepExecutionContext = stepExecution.executionContext
    def jobExecutionContext = stepExecution.jobExecution.executionContext

    @Subject
    def context = new ChunkContextAdapter(chunkContext)

    def "gets values from the step execution context"() {
        given:
        stepExecutionContext.put(key, value)

        expect:
        context[key] == value

        where:
        key = "foo"
        value = "bar"
    }

    def "gets values from the job execution context if not found in the step execution context"() {
        given:
        jobExecutionContext.put(key, value)

        expect:
        context[key] == value

        where:
        key = "foo"
        value = "bar"
    }

    def "prefers values from the step execution context if found in both"() {
        given:
        stepExecutionContext.put(key, "STEP_$value")
        jobExecutionContext.put(key, "JOB_$value")

        expect:
        context[key] == "STEP_$value"

        where:
        key = "foo"
        value = "bar"
    }

    def "values are put to the step execution context only"() {
        when:
        context[key] = value

        then:
        stepExecutionContext.get(key) == value

        and:
        !jobExecutionContext.containsKey(key)

        where:
        key = "foo"
        value = "bar"
    }

}
