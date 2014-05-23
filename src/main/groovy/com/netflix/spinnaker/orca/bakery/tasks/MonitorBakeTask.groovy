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
        def region = context["region"] as String
        def previousStatus = context["bake.status"] as BakeStatus

        def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlockingObservable().single()

        def taskResult = new TaskResult()
        taskResult.outputs["bake.status"] = newStatus
        switch (newStatus.state) {
            case BakeStatus.State.COMPLETED:
                taskResult.status = TaskResult.Status.SUCCEEDED
                break
            case BakeStatus.State.CANCELLED:
                taskResult.status = TaskResult.Status.FAILED
                break
            default:
                taskResult.status = TaskResult.Status.RUNNING
        }
        return taskResult
    }
}
