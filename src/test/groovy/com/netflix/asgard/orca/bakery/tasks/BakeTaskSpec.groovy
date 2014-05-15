package com.netflix.asgard.orca.bakery.tasks

import com.netflix.asgard.orca.bakery.api.BakeService
import com.netflix.asgard.orca.bakery.tasks.BakeTask
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import spock.lang.Specification
import spock.lang.Subject

class BakeTaskSpec extends Specification {

    @Subject
    def task = new BakeTask()

    def mockBakeService = Mock(BakeService)

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def jobExecution = new JobExecution(1, jobParameters)
    def stepExecution = new StepExecution("bakeStep", jobExecution)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def setup() {
        task.bakeService = mockBakeService
    }

    def "makes a POST request to Bakery"() {
        when:
        task.execute(stepContribution, chunkContext)

        then:
        1 * mockBakeService.createBake()
    }

}
