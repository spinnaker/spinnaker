'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const DCOS_SERVERGROUP_TRANSFORMER = 'spinnaker.dcos.serverGroup.transformer';
export const name = DCOS_SERVERGROUP_TRANSFORMER; // for backwards compatibility
module(DCOS_SERVERGROUP_TRANSFORMER, []).factory('dcosServerGroupTransformer', [
  '$q',
  function ($q) {
    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      const command = _.defaults({ backingData: [], viewState: [] }, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }

      command.availabilityZones = {};
      command.availabilityZones[command.region] = ['default'];

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
  },
]);
