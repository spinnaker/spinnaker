package com.netflix.kato.data.task

public interface Task {
  String getId()
  List<Status> getHistory()
  void updateStatus(String phase, String status)
  void complete()
  void fail()
  Status getStatus()
  long getStartTimeMs()
}