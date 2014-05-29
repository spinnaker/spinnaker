package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap

interface TaskResult {

    Status getStatus()

    ImmutableMap<String, Object> getOutputs()

    /**
     * Indicates the state of the workflow at the end of a call to {@link Task#execute}.
     */
    static enum Status {
        /**
         * The task is still running and the {@code Task} may be re-executed in order to continue.
         */
        RUNNING(false),

        /**
         * The task is complete but the workflow should now be stopped pending a trigger of some kind.
         */
            SUSPENDED(false),

        /**
         * The task executed successfully and the workflow may now proceed to the next task.
         */
            SUCCEEDED(true),

        /**
         * The task failed and the workflow should stop with an error.
         */
            FAILED(true)

        final boolean complete

        Status(boolean complete) {
            this.complete = complete
        }
    }
}

