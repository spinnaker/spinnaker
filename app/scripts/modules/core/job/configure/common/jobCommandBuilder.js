'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.job.configure.common.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../cache/deckCacheFactory.js'),
  require('../../../cloudProvider/serviceDelegate.service.js'),
  require('../../../config/settings.js')
])
  .factory('jobCommandBuilder', function (settings, Restangular, serviceDelegate) {

    function getJob(application, account, region, jobName) {
      return Restangular.one('applications', application).all('jobs').all(account).all(region).one(jobName).get();
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

