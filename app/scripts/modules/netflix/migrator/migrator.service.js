'use strict';

let angular = require('angular');

module.exports = angular

  .module('spinnaker.migrator.service', [
    require('../../core/utils/lodash.js'),
    require('../../core/task/taskExecutor.js'),
  ])
  .factory('migratorService', function($timeout, $q, _, taskExecutor) {

    function executeMigration(config) {
      var deferred = $q.defer();
      var task = { id: null, deferred: deferred, dryRun: config.dryRun };
      var taskStarter = taskExecutor.executeTask({
        application: config.application,
        description: 'Migrate ' + config.name + ' to VPC0',
        job: [{
          type: config.type,
          source: config.source,
          target: config.target,
          dryRun: config.dryRun,
        }]
      });

      taskStarter.then(
        function(execution) {
          task.id = execution.id;
          task.execution = execution;
          monitorTask(task);
        },
        function(error) {
          var message = error.message || 'An internal server error occurred. Please try again later.';
          task.deferred.reject(message);
        }
      );

      return task;
    }

    function monitorTask(task) {

      if (task.execution.isFailed) {
        var exception = task.execution.getValueFor('exception'),
            message = exception && exception.message ? exception.message : 'An internal server error occurred. Please try again later.';
        task.deferred.reject(message);
        return;
      }
      if (task.execution.isCompleted && task.execution.getValueFor('tide.task')) {
        if (task.dryRun) {
          task.executionPlan = buildPreview(task);
        }
        task.deferred.resolve();
      } else {
        if (!task.deferred.promise.cancelled) {
          task.lastResult = extractLastResult(task);
          $timeout(function() {
            task.execution.get().then(function(reloaded) {
              task.execution = reloaded;
              monitorTask(task);
            });
          }, 1000);
        }
      }

    }

    function getTideTask(task) {
      if (task.execution.getValueFor('tide.task')) {
        return task.execution.getValueFor('tide.task');
      }
      return {};
    }

    function extractLastResult(taskResult) {
      var tideTask = getTideTask(taskResult);
      taskResult.eventLog = _(tideTask.history).sortBy('timeStamp')
        .pluck('message')
        .valueOf();
      return taskResult;
    }

    function buildPreview(plan) {
      var tideTask = getTideTask(plan);
      tideTask.mutations = tideTask.mutations || [];
      return {
        securityGroups: tideTask.mutations.filter(function(mutation) { return mutationIs(mutation, 'groupName'); }),
        loadBalancers: tideTask.mutations.filter(function(mutation) { return mutationIs(mutation, 'loadBalancerName'); }),
        serverGroups: tideTask.mutations.filter(function(mutation) { return mutationIs(mutation, 'autoScalingGroupName'); }),
        pipelines: tideTask.mutations.filter(function(mutation) { return !!mutation.pipelineToCreate; }),
      };
    }

    function mutationIs(mutation, field) {
      return mutation.mutationDetails && mutation.mutationDetails.awsReference && mutation.mutationDetails.awsReference.identity && mutation.mutationDetails.awsReference.identity[field];
    }

    return {
      executeMigration: executeMigration,
    };

  });
