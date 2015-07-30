'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.tasks.detail.controller', [])
  .controller('TaskDetailsCtrl', function($scope, $log, $state,
                                          tasksReader, tasksWriter,
                                          taskId, application) {

    var vm = this;

    function extractTaskDetails() {
      var tasks = application.tasks || [];
      var filtered = tasks.filter(function(task) {
        return task.id === taskId;
      });
      if (!filtered.length) {
        tasksReader.getOneTaskForApplication(application.name, taskId).then(
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
      tasksWriter.cancelTask(application.name, taskId).then(application.reloadTasks);
    };

    return vm;
  }).name;
