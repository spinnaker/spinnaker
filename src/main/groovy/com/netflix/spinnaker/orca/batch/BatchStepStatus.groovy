package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.TaskResult
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.repeat.RepeatStatus

@Immutable(knownImmutables = ["exitStatus"])
@CompileStatic
class BatchStepStatus {

    RepeatStatus repeatStatus
    ExitStatus exitStatus

    static BatchStepStatus forTaskResult(TaskResult result) {
        switch (result.status) {
            case TaskResult.Status.SUCCEEDED:
                return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.COMPLETED)
            case TaskResult.Status.FAILED:
                return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.FAILED)
            case TaskResult.Status.RUNNING:
                return new BatchStepStatus(RepeatStatus.CONTINUABLE, ExitStatus.EXECUTING)
        }
    }
}
