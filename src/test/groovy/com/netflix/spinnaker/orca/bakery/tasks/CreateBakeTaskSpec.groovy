package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

    @Subject
    def task = new CreateBakeTask()

    final region = "us-west-1"
    final bakePackage = "hodor"
    final bakeUser = "bran"
    final bakeOs = Bake.OperatingSystem.ubuntu
    final bakeLabel = Bake.Label.release

    def context = Stub(TaskContext) {
        getAt("region") >> region
        getAt("bake.package") >> bakePackage
        getAt("bake.user") >> bakeUser
        getAt("bake.baseOs") >> bakeOs.name()
        getAt("bake.baseLabel") >> bakeLabel.name()
    }

    def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

    def "creates a bake for the correct region"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(context)

        then:
        1 * task.bakery.createBake(region, _ as Bake) >> Observable.from(runningStatus)
    }

    def "gets bake configuration from job context"() {
        given:
        task.bakery = Mock(BakeryService)

        when:
        task.execute(context)

        then:
        1 * task.bakery.createBake(*_) >> { String region, Bake bake ->
            assert bake.user == bakeUser
            assert bake.packageName == bakePackage
            assert bake.baseOs == bakeOs
            assert bake.baseLabel == bakeLabel
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
