'use strict';
let angular = require('angular');
module.exports =  angular.module('spinnaker.core.pipeline.stage.executionWindows.transformer', [])
  .service('executionWindowsTransformer', function() {

    //Overrides "running" status, setting it to "suspended"
    function transformRunningExecutionWindows(execution) {
      var hasRunningStage = false,
          inRestrictedWindow = false;
      execution.stages
        .filter(function (stage) {
          return stage.type === 'restrictExecutionDuringTimeWindow' && stage.status === 'RUNNING';
        })
        .forEach(function (stage) {
          inRestrictedWindow = true;
          stage.status = 'SUSPENDED';
          stage.tasks.forEach(function(task) { task.status = 'SUSPENDED'; });
        });

      if (inRestrictedWindow && execution.status === 'RUNNING') {
        hasRunningStage = execution.stages.some(function (stage) {
          return stage.status === 'RUNNING';
        });
        if (!hasRunningStage) {
          execution.status = 'SUSPENDED';
        }
      }
    }
    this.transform = function(application, execution) {
      transformRunningExecutionWindows(execution);
    };
  });
