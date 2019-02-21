'use strict';

const angular = require('angular');
import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';

import { OracleProviderSettings } from '../../oracle.settings';

module.exports = angular
  .module('spinnaker.oracle.serverGroupCommandBuilder.service', [])
  .factory('oracleServerGroupCommandBuilder', ['$q', function($q) {
    let oracle = 'oracle';

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      let defaultAccount = defaults.account || OracleProviderSettings.defaults.account;
      let defaultRegion = defaults.region || OracleProviderSettings.defaults.region;

      let command = {
        account: defaultAccount,
        application: application.name,
        capacity: {
          desired: 1,
        },
        region: defaultRegion,
        selectedProvider: oracle,
        viewState: {
          mode: defaults.mode || 'create',
          disableStrategySelection: true,
        },
      };

      return $q.when(command);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';
      let serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);

      let command = {
        account: serverGroup.account,
        application: application.name,
        shape: serverGroup.launchConfig.shape,
        strategy: '',
        stack: serverGroupName.stack,
        vpcId: serverGroup.launchConfig.vpcId,
        subnetId: serverGroup.launchConfig.subnetId,
        region: serverGroup.region,
        availabilityDomain: serverGroup.launchConfig.availabilityDomain,
        sshAuthorizedKeys: serverGroup.launchConfig.sshAuthorizedKeys,
        selectedProvider: oracle,
        capacity: {
          desired: serverGroup.capacity.desired,
        },
        viewState: {
          mode: mode,
          disableStrategySelection: true,
        },
      };
      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {
      let pipelineCluster = _.cloneDeep(originalCluster);
      let commandDefaults = { account: pipelineCluster.account, region: pipelineCluster.region };
      return buildNewServerGroupCommand(application, commandDefaults).then(command => {
        let viewState = {
          disableImageSelection: true,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          templatingEnabled: true,
        };

        let viewOverrides = {
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';
        return angular.extend({}, command, pipelineCluster, viewOverrides);
      });
    }

    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        },
      });
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
  }]);
