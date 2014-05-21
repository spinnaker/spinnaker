package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Step
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.StepResult.COMPLETE
import static com.netflix.spinnaker.orca.StepResult.INCOMPLETE
import static org.springframework.batch.repeat.RepeatStatus.CONTINUABLE
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

class StepTaskletSpec extends Specification {

    def step = Mock(Step)

    @Subject
    def tasklet = new StepTasklet(step)

    def "should invoke the step when executed"() {
        when:
        tasklet.execute(null, null)

        then:
        1 * step.execute(*_)
    }

    def "should convert step return status to equivalent batch status"() {
        given:
        step.execute() >> stepResult

        expect:
        tasklet.execute(null, null) == expectedResult

        where:
        stepResult | expectedResult
        COMPLETE   | FINISHED
        INCOMPLETE | CONTINUABLE
    }

}
