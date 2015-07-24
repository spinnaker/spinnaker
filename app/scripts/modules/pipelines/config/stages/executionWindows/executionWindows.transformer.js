'use strict';
let angular = require('angular');
module.exports =  angular.module('spinnaker.pipelines.stage.executionWindows.transformer', [])
  .service('executionWindowsTransformer', function() {

    //Overrides "running" status, setting it to "suspended"
    function transformRunningExecutionWindows(execution) {
      execution.stages
        .filter(function (stage) {
          return stage.type === 'restrictExecutionDuringTimeWindow' && stage.status === 'RUNNING';
        })
        .forEach(function (stage) {
          stage.status = 'SUSPENDED';
          stage.tasks.forEach(function(task) { task.status = 'SUSPENDED'; });
        });
    }
    this.transform = function(application, execution) {
      transformRunningExecutionWindows(execution);
    };
  })
  .name;
