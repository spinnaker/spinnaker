package com.netflix.kato.data.task

public interface TaskRepository {
  Task create(String phase, String status)
  Task get(String id)
  List<Task> list()
}