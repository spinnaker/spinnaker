package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.TaskResult
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.repeat.RepeatStatus

@CompileStatic
class BatchStepStatus {

    final RepeatStatus repeatStatus
    final ExitStatus exitStatus

    private BatchStepStatus(RepeatStatus repeatStatus, ExitStatus exitStatus) {
        this.repeatStatus = repeatStatus
        this.exitStatus = exitStatus
    }

    static BatchStepStatus forTaskResult(TaskResult result) {
        switch (result) {
            case TaskResult.SUCCEEDED:
                return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.COMPLETED)
            case TaskResult.FAILED:
                return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.FAILED)
            case TaskResult.RUNNING:
                return new BatchStepStatus(RepeatStatus.CONTINUABLE, ExitStatus.EXECUTING)
        }
    }
}
