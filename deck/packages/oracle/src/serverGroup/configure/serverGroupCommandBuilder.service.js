'use strict';

import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';

import { OracleProviderSettings } from '../../oracle.settings';

const oracle = 'oracle';

export class OracleServerGroupCommandBuilder {
  buildNewServerGroupCommand(application, defaults) {
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

    return Promise.resolve(command);
  }

  buildServerGroupCommandFromExisting(application, serverGroup, mode) {
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
    return Promise.resolve(command);
  }

  buildServerGroupCommandFromPipeline(application, originalCluster) {
    const pipelineCluster = _.cloneDeep(originalCluster);
    const commandDefaults = { account: pipelineCluster.account, region: pipelineCluster.region };
    return this.buildNewServerGroupCommand(application, commandDefaults).then((command) => {
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
      return _.assign({}, command, pipelineCluster, viewOverrides);
    });
  }

  buildNewServerGroupCommandForPipeline() {
    return Promise.resolve({
      viewState: {
        requiresTemplateSelection: true,
      },
    });
  }
}
