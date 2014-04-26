package com.netflix.kato.data.task

public interface TaskRepository {
  static final ThreadLocal<Task> threadLocalTask = new ThreadLocal<>()

  Task create(String phase, String status)
  Task get(String id)
  List<Task> list()
}