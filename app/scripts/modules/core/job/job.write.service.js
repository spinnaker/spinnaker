'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.job.write.service', [
    require('../task/taskExecutor.js'),
    require('./job.transformer.js'),
  ])
  .factory('jobWriter', function (taskExecutor, jobTransformer) {
    function cloneJob(command, application) {
      var description;
      if (command.viewState.mode === 'clone') {
        description = 'Create Cloned Job from ' + command.source.jobName;
        command.type = 'cloneJob';
      } else {
        command.type = 'runJob';
        var jobName = application.name;
        if (command.stack) {
          jobName += '-' + command.stack;
        }
        if (!command.stack && command.freeFormDetails) {
          jobName += '-';
        }
        if (command.freeFormDetails) {
          jobName += '-' + command.freeFormDetails;
        }
        description = 'Create New Job in cluster ' + jobName;
      }

      return taskExecutor.executeTask({
        job: [
          jobTransformer.convertJobCommandToRunConfiguration(command)
        ],
        application: application,
        description: description
      });
    }


    return {
      cloneJob: cloneJob
    };
  });
