'use strict';


angular.module('deckApp.tasks.main', ['deckApp.utils.lodash'])
  .controller('TasksCtrl', function ($scope, application, _) {
    var self = this;
    self.taskStateFilter = 'All';
    self.application = application;

    self.sortedTasks = [];

    self.sortTasks = function() {
      var joinedLists = filterRunningTasks().concat(filterNonRunningTasks());
      self.sortedTasks = joinedLists;
      return joinedLists;
    };

    self.sortTasks();

    application.registerAutoRefreshHandler(self.sortTasks, $scope);

    function filterRunningTasks() {
      var running = _.chain(application.tasks)
        .filter(function(task) {
          return task.status === 'RUNNING';
        })
        .value();


      var ordered = running.sort(taskStartTimeComparitor);
      return ordered;
    }

    function filterNonRunningTasks() {
      var notRunning = _.chain(application.tasks)
        .filter(function(task) {
          return task.status !== 'RUNNING';
        })
        .value();

      var ordered = notRunning.sort(taskStartTimeComparitor);
      return ordered;
    }

    function taskStartTimeComparitor(taskA, taskB) {
      return taskB.startTime > taskA.startTime ? 1 : taskB.startTime < taskA.startTime ? -1 : 0;
    }

    return self;
  }
);
