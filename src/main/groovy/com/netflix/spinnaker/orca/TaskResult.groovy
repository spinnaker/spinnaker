package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic

@CompileStatic
class TaskResult {

    Status status

    static enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

}