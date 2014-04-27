package com.netflix.kato.data.task

import java.util.concurrent.ConcurrentHashMap

class InMemoryTaskRepository implements TaskRepository {
  private final Map<String, Task> repository = new ConcurrentHashMap<>()

  @Override
  Task create(String phase, String status) {
    def task = new DefaultTask(nextId, phase, status)
    repository[task.id] = task
  }

  @Override
  Task get(String id) {
    repository?.get(id)
  }

  @Override
  List<Task> list() {
    repository.values() as List
  }

  private String getNextId() {
    def maybeNext = new BigInteger(new Random().nextInt(Integer.MAX_VALUE)).toString(36)
    while (true) {
      if (!repository.containsKey(maybeNext)) {
        break
      }
    }
    maybeNext
  }
}
