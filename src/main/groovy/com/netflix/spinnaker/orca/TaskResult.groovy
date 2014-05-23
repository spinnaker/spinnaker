package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic

@CompileStatic
// TODO: I think it would make sense to make this immutable and provide a builder
final class TaskResult {

    Status status
    final Map<String, Object> outputs = [:]

    static enum Status {
        RUNNING(false),
        SUCCEEDED(true),
        FAILED(true)

        final boolean complete

        Status(boolean complete) {
            this.complete = complete
        }
    }

}