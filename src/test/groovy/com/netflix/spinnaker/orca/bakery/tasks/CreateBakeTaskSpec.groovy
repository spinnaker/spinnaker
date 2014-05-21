package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class CreateBakeTaskSpec extends Specification {

    @Subject
    def task = new CreateBakeTask()

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def stepExecution = createStepExecution(jobParameters)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

    def setup() {
        stepExecution.jobExecution.executionContext.with {
            putString "bake.package", "hodor"
            putString "bake.user", "bran"
            putString "bake.baseOs", "ubuntu"
            putString "bake.baseLabel", "release"
        }
    }

    def "creates a bake for the correct region"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(stepContribution, chunkContext)

        then:
        1 * task.bakery.createBake(region, _ as Bake) >> Observable.from(runningStatus)
    }

    def "gets bake configuration from job context"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(stepContribution, chunkContext)

        then:
        1 * task.bakery.createBake(*_) >> { String region, Bake bake ->
            assert bake.user == stepContext.jobExecutionContext."bake.user"
            assert bake.packageName == stepContext.jobExecutionContext."bake.package"
            assert bake.baseOs.name() == stepContext.jobExecutionContext."bake.baseOs"
            assert bake.baseLabel.name() == stepContext.jobExecutionContext."bake.baseLabel"
            Observable.from(runningStatus)
        }
    }

    def "stores the status of the bake in the job context"() {
        given:
        task.bakery = Stub(BakeryService) {
            createBake(*_) >> Observable.from(runningStatus)
        }

        when:
        task.execute(stepContribution, chunkContext)

        then:
        with(stepContext.jobExecutionContext["bake.status"]) {
            id == runningStatus.id
            state == runningStatus.state
        }
    }

}
