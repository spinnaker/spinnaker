package com.netflix.spinnaker.orca

interface TaskContext {

    Map<String, Object> getInputs()

}