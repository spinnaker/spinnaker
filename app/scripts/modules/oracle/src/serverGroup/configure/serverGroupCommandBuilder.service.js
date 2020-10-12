'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';

import { OracleProviderSettings } from '../../oracle.settings';

export const ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE =
  'spinnaker.oracle.serverGroupCommandBuilder.service';
export const name = ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE; // for backwards compatibility
angular
  .module(ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE, [])
  .factory('oracleServerGroupCommandBuilder', [
    '$q',
    function ($q) {
      const oracle = 'oracle';

      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};

        const defaultAccount = defaults.account || OracleProviderSettings.defaults.account;
        const defaultRegion = defaults.region || OracleProviderSettings.defaults.region;

        const command = {
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
        const serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);

        const command = {
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
        const pipelineCluster = _.cloneDeep(originalCluster);
        const commandDefaults = { account: pipelineCluster.account, region: pipelineCluster.region };
        return buildNewServerGroupCommand(application, commandDefaults).then((command) => {
          const viewState = {
            disableImageSelection: true,
            mode: 'editPipeline',
            submitButtonLabel: 'Done',
            templatingEnabled: true,
          };

          const viewOverrides = {
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
    },
  ]);
