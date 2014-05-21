package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

    @Subject
    def task = new CreateBakeTask()

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def jobExecution = new JobExecution(1, jobParameters)
    def stepExecution = new StepExecution("bakeStep", jobExecution)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def "creates a bake for the correct region"() {
        given:
        def mockBakeService = Mock(BakeryService)
        task.bakery = mockBakeService

        when:
        task.execute(stepContribution, chunkContext)

        then:
        1 * mockBakeService.createBake(region) >> Observable.empty()
    }

    def "stores the status of the bake in the job context"() {
        given:
        def bakeStatus = new BakeStatus(id: randomUUID(), state: RUNNING)
        task.bakery = Stub(BakeryService) {
            createBake(region) >> Observable.from(bakeStatus)
        }

        when:
        task.execute(stepContribution, chunkContext)

        then:
        stepContext.jobExecutionContext["bake.status"].is bakeStatus
    }

}
