'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.transformer', [
    require('../../core/utils/lodash.js'),
  ])
  .factory('kubernetesServerGroupTransformer', function ($q, _) {

    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      command.cloudProvider = 'kubernetes';
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;

      command.region = command.namespace;

      command.containers.forEach(function transformContainerCommand(element, index, array) {
        delete array[index].accountName;
        delete array[index].imageId;
      });

      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
