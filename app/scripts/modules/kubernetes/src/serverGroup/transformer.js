'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.transformer', [])
  .factory('kubernetesServerGroupTransformer', ['$q', function($q) {
    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({ backingData: [], viewState: [] }, base);
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

      if (!base.viewState.useAutoscaler) {
        delete command.scalingPolicy;
        delete command.capacity;
      }

      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };
  }]);
