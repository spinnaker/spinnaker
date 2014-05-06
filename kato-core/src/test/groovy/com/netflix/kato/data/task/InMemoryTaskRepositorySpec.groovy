package com.netflix.kato.data.task

import spock.lang.Shared
import spock.lang.Specification

class InMemoryTaskRepositorySpec extends Specification {

  @Shared
  InMemoryTaskRepository taskRepository

  def setupSpec() {
    resetTaskRepository()
  }

  void resetTaskRepository() {
    this.taskRepository = new InMemoryTaskRepository()
  }

  void cleanup() {
    resetTaskRepository()
  }

  void "creating a new task returns task with unique id"() {
    given:
      def t1 = taskRepository.create("TEST", "Test Status")
      def t2 = taskRepository.create("TEST", "Test Status")

    expect:
      t1.id != t2.id
  }

  void "looking up a task by id returns the same task"() {
    setup:
      def t1 = taskRepository.create("TEST", "Test Status")

    when:
      def t2 = taskRepository.get(t1.id)

    then:
      t1.is t2
  }

  void "listing tasks returns all avilable tasks"() {
    setup:
      def t1 = taskRepository.create "TEST", "Test Status"
      def t2 = taskRepository.create "TEST", "Test Status"

    when:
      def list = taskRepository.list()

    then:
      list.containsAll([t1, t2])
  }
}
