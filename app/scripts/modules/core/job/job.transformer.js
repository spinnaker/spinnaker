'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.job.transformer', [
  require('../cloudProvider/serviceDelegate.service.js'),
])
  .factory('jobTransformer', function (serviceDelegate) {

    function normalizeJob(job) {
      job.region = job.region || job.location;
      job.type = job.type || job.provider;
      return serviceDelegate.getDelegate(job.provider || job.type, 'job.transformer').
        normalizeJob(job);
    }

    function convertJobCommandToDeployConfiguration(base) {
      var service = serviceDelegate.getDelegate(base.selectedProvider, 'job.transformer');
      return service ? service.convertJobCommandToDeployConfiguration(base) : null;
    }

    // strips out Angular bits (see angular.js#toJsonReplacer), as well as executions and running tasks
    function jsonReplacer(key, value) {
      var val = value;

      if (typeof key === 'string' && key.charAt(0) === '$' && key.charAt(1) === '$') {
        val = undefined;
      }

      if (key === 'executions' || key === 'runningTasks') {
        val = undefined;
      }

      return val;
    }

    return {
      normalizeJob: normalizeJob,
      convertJobCommandToDeployConfiguration: convertJobCommandToDeployConfiguration,
      jsonReplacer: jsonReplacer,
    };

  });
