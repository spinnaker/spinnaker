package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic

interface TaskResult {

    Status getStatus()

    ImmutableMap<String, Object> getOutputs()

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

