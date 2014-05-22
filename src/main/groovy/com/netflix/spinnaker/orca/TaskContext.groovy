package com.netflix.spinnaker.orca

interface TaskContext {

    def <T> T getAt(String key)

    boolean containsKey(String key)
}