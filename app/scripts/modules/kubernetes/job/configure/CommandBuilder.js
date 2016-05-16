'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.jobCommandBuilder.service', [
  require('../../../core/config/settings.js'),
  require('../../../core/utils/lodash.js'),
  require('../../cluster/cluster.kubernetes.module.js'),
])
  .factory('kubernetesJobCommandBuilder', function (settings, $q, kubernetesClusterCommandBuilder) {
    function buildNewJobCommand(application, defaults = {}) {
      var command = kubernetesClusterCommandBuilder.buildNewClusterCommand(application, defaults);
      command.parallelism = 1;
      command.completions = 1;

      return $q.when(command);
    }

    function buildJobCommandFromExisting(application, job, mode) {
      var command = kubernetesClusterCommandBuilder.buildClusterCommandFromExisting(application, job, mode);

      command.source = {
        jobName: job.name,
        account: job.account,
        region: job.region,
        namespace: job.region,
      };

      return $q.when(command);
    }

    function buildNewJobCommandForPipeline(current, pipeline) {
      return $q.when(kubernetesClusterCommandBuilder.buildNewClusterCommandForPipeline(current, pipeline));
    }

    function buildJobCommandFromPipeline(app, command, current, pipeline) {
      return $q.when(kubernetesClusterCommandBuilder.buildClusterCommandFromPipeline(app, command, current, pipeline));
    }

    return {
      buildNewJobCommand: buildNewJobCommand,
      buildJobCommandFromExisting: buildJobCommandFromExisting,
      buildNewJobCommandForPipeline: buildNewJobCommandForPipeline,
      buildJobCommandFromPipeline: buildJobCommandFromPipeline,
    };
  });
