package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class MonitorBakeTask implements Task {

    @Autowired
    BakeryService bakery

    @Override
    TaskResult execute(TaskContext context) {
        def region = context.inputs.region as String
        def previousStatus = context.inputs."bake.status" as BakeStatus

        def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlockingObservable().single()

        new TaskResult(mapStatus(newStatus), ["bake.status": newStatus])
    }

    private TaskResult.Status mapStatus(BakeStatus newStatus) {
        switch (newStatus.state) {
            case BakeStatus.State.COMPLETED:
                return TaskResult.Status.SUCCEEDED
            case BakeStatus.State.CANCELLED:
                return TaskResult.Status.FAILED
            default:
                return TaskResult.Status.RUNNING
        }
    }
}
