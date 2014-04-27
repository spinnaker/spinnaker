package com.netflix.kato.data.task

/**
 * A TaskRepository is an implementation that allows Task objects to be created, retrieved, and listed on demand.
 *
 * @see Task
 * @see InMemoryTaskRepository
 * @author Dan Woods
 */
public interface TaskRepository {
  /**
   * A thread local holder for a Task in-action. Useful for the {@link InMemoryTaskRepository} implementation.
   */
  static final ThreadLocal<Task> threadLocalTask = new ThreadLocal<>()

  /**
   * Creates a new task, and sets the initial status to the provided phase and status.
   *
   * @param phase
   * @param status
   * @return task
   */
  Task create(String phase, String status)

  /**
   * Retrieves a task by the provided id
   *
   * @param id
   * @return task
   */
  Task get(String id)

  /**
   * Lists all tasks currently in the repository
   *
   * @return list of tasks
   */
  List<Task> list()
}