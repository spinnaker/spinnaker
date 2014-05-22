package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
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

    def "gets values from the step context"() {
        given:
        stepExecutionContext.put("foo", "bar")

        expect:
        context["foo"] == "bar"
    }

}
