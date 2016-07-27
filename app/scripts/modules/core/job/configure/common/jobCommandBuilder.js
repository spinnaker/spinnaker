'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.job.configure.common.service', [
  require('../../../cache/deckCacheFactory.js'),
  require('../../../cloudProvider/serviceDelegate.service.js'),
  require('../../../config/settings.js'),
  require('../../job.read.service')
])
  .factory('jobCommandBuilder', function (settings, serviceDelegate, jobReader) {

    function getJob(application, account, region, jobName) {
      return jobReader.getJob(application, account, region, jobName);
    }

    function getDelegate(provider) {
      return serviceDelegate.getDelegate(provider, 'job.commandBuilder');
    }

    function buildNewJobCommand(application, provider, options) {
      return getDelegate(provider).buildNewJobCommand(application, options);
    }

    function buildJobCommandFromExisting(application, job, mode) {
      return getDelegate(job.type).buildJobCommandFromExisting(application, job, mode);
    }

    function buildNewJobCommandForPipeline(provider, currentStage, pipeline) {
      return getDelegate(provider).buildNewJobCommandForPipeline(currentStage, pipeline);
    }

    function buildJobCommandFromPipeline(application, cluster, currentStage, pipeline) {
      return getDelegate(cluster.provider).buildJobCommandFromPipeline(application, cluster, currentStage, pipeline);
    }

    return {
      getJob: getJob,
      buildNewJobCommand: buildNewJobCommand,
      buildJobCommandFromExisting: buildJobCommandFromExisting,
      buildNewJobCommandForPipeline: buildNewJobCommandForPipeline,
      buildJobCommandFromPipeline: buildJobCommandFromPipeline,
    };
});

