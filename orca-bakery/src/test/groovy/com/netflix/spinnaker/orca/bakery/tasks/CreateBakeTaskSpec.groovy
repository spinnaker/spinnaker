package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

    @Subject task = new CreateBakeTask()
    def context = new SimpleTaskContext()
    def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

    def setup() {
        context.region = "us-west-1"
        context."bake.package" = "hodor"
        context."bake.user" = "bran"
        context."bake.baseOs" = Bake.OperatingSystem.ubuntu.name()
        context."bake.baseLabel" = Bake.Label.release.name()
    }

    def "creates a bake for the correct region"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(context)

        then:
        1 * task.bakery.createBake(context.region, _ as Bake) >> Observable.from(runningStatus)
    }

    def "gets bake configuration from job context"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(context)

        then:
        1 * task.bakery.createBake(*_) >> { String region, Bake bake ->
            assert bake.user == context."bake.user"
            assert bake.packageName == context."bake.package"
            assert bake.baseOs.name() == context."bake.baseOs"
            assert bake.baseLabel.name() == context."bake.baseLabel"
            Observable.from(runningStatus)
        }
    }

    def "outputs the status of the bake"() {
        given:
        task.bakery = Stub(BakeryService) {
            createBake(*_) >> Observable.from(runningStatus)
        }

        when:
        def result = task.execute(context)

        then:
        with(result.outputs["bake.status"]) {
            id == runningStatus.id
            state == runningStatus.state
        }
    }

}
