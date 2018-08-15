package com.netflix.spinnaker.clouddriver.ecs.controllers.servergroup;

import com.amazonaws.services.ecs.model.ServiceEvent;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroupEventStatus;
import org.springframework.stereotype.Service;

@Service
public class ServerGroupEventStatusConverter {

  private static final String ERROR_TYPE_1 = "unable to place a task";
  private static final String ERROR_TYPE_2 = "is unhealthy";

  private static final String SUCCESS_TYPE_1 = "has reached a steady state";

  public EcsServerGroupEventStatus inferEventStatus(ServiceEvent event) {

    String message = event.getMessage();

    if (message.contains(ERROR_TYPE_1) || message.contains(ERROR_TYPE_2)) {
      return EcsServerGroupEventStatus.Failure;
    } else if (message.contains(SUCCESS_TYPE_1)) {
      return EcsServerGroupEventStatus.Success;
    } else {
      return EcsServerGroupEventStatus.Transition;
    }
  }
}
