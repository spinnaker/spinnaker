package com.netflix.spinnaker.orca

interface Task {

    TaskResult execute(TaskContext context)

}