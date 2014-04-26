package com.netflix.kato.data.task

public interface Status {
  String getPhase()
  String getStatus()
  Boolean isCompleted()
  Boolean isFailed()
}