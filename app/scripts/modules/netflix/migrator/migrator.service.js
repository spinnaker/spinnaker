'use strict';

let angular = require('angular');

module.exports = angular

  .module('spinnaker.migrator.service', [
    require('../../core/utils/lodash.js'),
    require('../../core/task/taskExecutor.js'),
  ])
  .factory('migratorService', function(_, taskExecutor) {

    function executeMigration(config) {
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

      return taskStarter.then((task) => {
        task.getEventLog = () => getEventLog(task);
        task.getTideException = () => getTideException(task);
        task.getPreview = () => getPreview(task);
        return task;
      });
    }

    function getTideTask(task) {
      if (task.getValueFor('tide.task')) {
        return task.getValueFor('tide.task');
      }
      return {};
    }

    function getEventLog(task) {
      var tideTask = getTideTask(task);
      return _(tideTask.history).sortBy('timeStamp')
        .pluck('message')
        .valueOf();
    }

    function getTideException(task) {
      let tideTask = getTideTask(task);
      if (tideTask.taskComplete && tideTask.taskComplete.status === 'failure') {
        return tideTask.taskComplete.message;
      }
      return null;
    }

    function getPreview(task) {
      var tideTask = getTideTask(task) || {};
      tideTask.mutations = tideTask.mutations || [];
      return {
        securityGroups: tideTask.mutations.filter((mutation) => mutationIs(mutation, 'groupName')),
        loadBalancers: tideTask.mutations.filter((mutation) => mutationIs(mutation, 'loadBalancerName')),
        serverGroups: tideTask.mutations.filter((mutation) => mutationIs(mutation, 'autoScalingGroupName')),
        pipelines: tideTask.mutations.filter((mutation) => mutation.pipelineToCreate),
      };
    }

    function mutationIs(mutation, field) {
      return _.has(mutation, 'mutationDetails.awsReference.identity.' + field);
    }

    return {
      executeMigration: executeMigration,
    };

  });
