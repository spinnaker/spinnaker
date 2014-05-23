package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
final class TaskResult {

    final Status status
    final ImmutableMap<String, Object> outputs

    TaskResult(Status status) {
        this(status, [:])
    }

    TaskResult(Status status, Map<String, ? extends Object> outputs) {
        this.status = status
        this.outputs = ImmutableMap.copyOf(outputs)
    }

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