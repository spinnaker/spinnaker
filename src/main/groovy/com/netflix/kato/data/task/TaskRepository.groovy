package com.netflix.kato.data.task

/**
 * A TaskRepository is an implementation that allows Task objects to be created, retrieved, and listed on demand.
 *
 * @author Dan Woods
 */
public interface TaskRepository {
  static final ThreadLocal<Task> threadLocalTask = new ThreadLocal<>()

  Task create(String phase, String status)
  Task get(String id)
  List<Task> list()
}