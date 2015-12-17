'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.task.details.controller', [])
  .controller('TaskDetailsCtrl', function($scope, $log, $state,
                                          taskReader, taskWriter,
                                          taskId, application) {

    var vm = this;

    function extractTaskDetails() {
      var tasks = application.tasks || [];
      var filtered = tasks.filter(function(task) {
        return task.id === taskId;
      });
      if (!filtered.length) {
        taskReader.getOneTaskForApplication(application.name, taskId).then(
          function(result) {
            vm.task = result;
          },
          function () {
            $state.go('^');
          }
        );
      } else {
        vm.task = filtered[0];
      }
    }

    extractTaskDetails();

    application.registerAutoRefreshHandler(extractTaskDetails, $scope);

    vm.retry = angular.noop;
    vm.cancel = function() {
      taskWriter.cancelTask(application.name, taskId).then(application.reloadTasks);
    };

    return vm;
  });
