'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.titus.serverGroup.transformer', [])
  .factory('titusServerGroupTransformer', function ($q) {

    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      command.account = command.credentials;
      if (command.resources.allocateIpAddress === true) {
        delete command.resources.ports;
      }

      if (!command.efs.mountPoint || !command.efs.efsId || !command.efs.mountPerm) {
        delete command.efs;
      }

      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
