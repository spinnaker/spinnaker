package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.util.UUID.randomUUID

class MonitorBakeTaskSpec extends Specification {

    @Subject
    def task = new MonitorBakeTask()

    final region = "us-west-1"

    def context = Stub(TaskContext) {
        getAt("region") >> region
    }

    @Unroll
    def "should return #taskStatus if bake is #bakeState"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
        context.getAt("bake.status") >> previousStatus

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: bakeState))
        }

        expect:
        task.execute(context).status == taskStatus

        where:
        bakeState                  | taskStatus
        BakeStatus.State.PENDING   | TaskResult.Status.RUNNING
        BakeStatus.State.RUNNING   | TaskResult.Status.RUNNING
        BakeStatus.State.COMPLETED | TaskResult.Status.SUCCEEDED
        BakeStatus.State.CANCELLED | TaskResult.Status.FAILED
        BakeStatus.State.SUSPENDED | TaskResult.Status.RUNNING

        id = randomUUID().toString()
    }

    def "outputs the updated bake status"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
        context.getAt("bake.status") >> previousStatus

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: BakeStatus.State.COMPLETED))
        }

        when:
        def result = task.execute(context)

        then:
        with(result.outputs["bake.status"]) {
            id == previousStatus.id
            state == BakeStatus.State.COMPLETED
        }

        where:
        id = randomUUID().toString()
    }

}
