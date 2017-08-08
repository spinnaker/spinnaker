'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.transformer', [])
  .factory('dcosServerGroupTransformer', function ($q) {

    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {


      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }

      command.availabilityZones = {};
      command.availabilityZones[command.region] = [];

      command.cloudProvider = 'dcos';
      command.credentials = command.account;

      delete command.viewState;
      delete command.viewModel;
      delete command.backingData;
      delete command.selectedProvider;

      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
