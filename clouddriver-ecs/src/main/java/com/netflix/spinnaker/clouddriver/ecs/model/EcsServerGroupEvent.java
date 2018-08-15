package com.netflix.spinnaker.clouddriver.ecs.model;

import lombok.Data;

import java.util.Date;

@Data
public class EcsServerGroupEvent {

  String message;
  Date createdAt;
  String id;
  EcsServerGroupEventStatus status;

  public EcsServerGroupEvent(String message, Date createdAt, String id, EcsServerGroupEventStatus status) {
    this.message = message;
    this.createdAt = createdAt;
    this.id = id;
    this.status = status;
  }

  public EcsServerGroupEvent() {
  }

}
