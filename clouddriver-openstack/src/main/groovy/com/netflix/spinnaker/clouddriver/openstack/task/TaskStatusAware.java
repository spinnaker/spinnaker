package com.netflix.spinnaker.clouddriver.openstack.task;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;

/**
 * TODO - Refeactor operations to use trait and remove boilerplate logic.
 */
public interface TaskStatusAware {
  default Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  String UPSERT_LOADBALANCER_PHASE = "UPSERT_LOAD_BALANCER";
}
