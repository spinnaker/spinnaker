'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.job.transformer', [ ])
  .factory('kubernetesJobTransformer', function ($q) {

    function normalizeJob(job) {
      return $q.when(job); // no-op
    }

    function convertJobCommandToRunConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      command.cloudProvider = 'kubernetes';
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.interestingHealthProviderNames;

      command.region = command.namespace;

      command.containers.forEach(function transformContainerCommand(element, index, array) {
        delete array[index].accountName;
        delete array[index].imageId;
      });

      return command;
    }

    return {
      convertJobCommandToRunConfiguration: convertJobCommandToRunConfiguration,
      normalizeJob: normalizeJob,
    };
  });
