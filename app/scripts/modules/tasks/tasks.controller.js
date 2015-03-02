'use strict';


angular.module('deckApp.tasks.main', ['deckApp.utils.lodash'])
  .controller('TasksCtrl', function ($scope, application, _) {
    var self = this;
    self.taskStateFilter = 'All';
    self.application = application;

    self.sortedTasks = [];
    self.tasksLoaded = false;

    self.sortTasks = function() {
      if (application.tasks) {
        self.tasksLoaded = true;
      }
      var joinedLists = filterRunningTasks().concat(filterNonRunningTasks());
      self.sortedTasks = joinedLists;
      return joinedLists;
    };

    $scope.$watch('application.tasks', self.sortTasks);

    function filterRunningTasks() {
      var running = _.chain(application.tasks)
        .filter(function(task) {
          return task.status === 'RUNNING';
        })
        .value();

      return running.sort(taskStartTimeComparator);
    }

    function filterNonRunningTasks() {
      var notRunning = _.chain(application.tasks)
        .filter(function(task) {
          return task.status !== 'RUNNING';
        })
        .value();

      return notRunning.sort(taskStartTimeComparator);
    }

    function taskStartTimeComparator(taskA, taskB) {
      return taskB.startTime > taskA.startTime ? 1 : taskB.startTime < taskA.startTime ? -1 : 0;
    }

    return self;
  }
);
