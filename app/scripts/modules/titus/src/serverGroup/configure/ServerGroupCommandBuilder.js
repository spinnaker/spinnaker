'use strict';

import * as angular from 'angular';

import { set } from 'lodash';
import { NameUtils } from '@spinnaker/core';
import { TitusProviderSettings } from '../../titus.settings';

export const TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER =
  'spinnaker.titus.serverGroupCommandBuilder.service';
export const name = TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER; // for backwards compatibility
angular.module(TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER, []).factory('titusServerGroupCommandBuilder', [
  '$q',
  function ($q) {
    function getDefaultIamProfile(application) {
      const defaultIamProfile = TitusProviderSettings.defaults.iamProfile || '{{application}}InstanceProfile';
      return defaultIamProfile.replace('{{application}}', application.name);
    }

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      const defaultCredentials = defaults.account || TitusProviderSettings.defaults.account;
      const defaultRegion = defaults.region || TitusProviderSettings.defaults.region;
      const defaultZone = defaults.zone || TitusProviderSettings.defaults.zone;
      const defaultIamProfile = getDefaultIamProfile(application);

      const command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        zone: defaultZone,
        network: 'default',
        inService: true,
        resources: {
          cpu: 1,
          networkMbps: 128,
          disk: 10000,
          memory: 512,
          gpu: 0,
        },
        efs: {
          mountPerm: 'RW',
        },
        strategy: '',
        capacity: {
          min: 1,
          max: 1,
          desired: 1,
        },
        targetHealthyDeployPercentage: 100,
        env: {},
        labels: {},
        containerAttributes: {
          'titusParameter.agent.assignIPv6Address': 'true',
        },
        cloudProvider: 'titus',
        selectedProvider: 'titus',
        iamProfile: defaultIamProfile,
        constraints: {
          hard: {},
          soft: {},
        },
        serviceJobProcesses: {},
        viewState: {
          defaultIamProfile,
          useSimpleCapacity: true,
          usePreferredZones: true,
          mode: defaults.mode || 'create',
        },
        securityGroups: [],
        imageId: defaults.imageId,
        migrationPolicy: { type: 'systemDefault' },
        digest: '',
        organization: '',
        tag: '',
        registry: '',
        repository: '',
      };

      return $q.when(command);
    }

    // Only used to prepare view requiring template selecting
    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        },
      });
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      const serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);

      const isTestEnv = serverGroup.awsAccount === 'test';
      const isIPv6Set =
        serverGroup.containerAttributes &&
        serverGroup.containerAttributes['titusParameter.agent.assignIPv6Address'] !== undefined;

      // If IPv6 hasn't been explicitly set by the user, auto-assign based on the environment.
      const assignIPv6Address = isIPv6Set
        ? serverGroup.containerAttributes['titusParameter.agent.assignIPv6Address']
        : isTestEnv
        ? 'true'
        : 'false';

      const containerAttributes = {
        ...serverGroup.containerAttributes,
        'titusParameter.agent.assignIPv6Address': assignIPv6Address,
      };

      const command = {
        application: application.name,
        disruptionBudget: serverGroup.disruptionBudget,
        strategy: '',
        stack: serverGroupName.stack,
        freeFormDetails: serverGroupName.freeFormDetails,
        account: serverGroup.account,
        credentials: serverGroup.account,
        region: serverGroup.region,
        env: serverGroup.env,
        labels: serverGroup.labels,
        containerAttributes,
        entryPoint: serverGroup.entryPoint,
        iamProfile: serverGroup.iamProfile,
        capacityGroup: serverGroup.capacityGroup,
        migrationPolicy: serverGroup.migrationPolicy ? serverGroup.migrationPolicy : { type: 'systemDefault' },
        securityGroups: serverGroup.securityGroups || [],
        constraints: {
          hard: (serverGroup.constraints && serverGroup.constraints.hard) || {},
          soft: (serverGroup.constraints && serverGroup.constraints.soft) || {},
        },
        serviceJobProcesses: Object.assign({}, serverGroup.serviceJobProcesses),
        inService: serverGroup.disabled ? false : true,
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          asgName: serverGroup.name,
        },
        resources: {
          cpu: serverGroup.resources.cpu,
          gpu: serverGroup.resources.gpu,
          memory: serverGroup.resources.memory,
          disk: serverGroup.resources.disk,
          networkMbps: serverGroup.resources.networkMbps,
        },
        targetGroups: serverGroup.targetGroups,
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired,
        },
        targetHealthyDeployPercentage: 100,
        cloudProvider: 'titus',
        selectedProvider: 'titus',
        viewState: {
          defaultIamProfile: getDefaultIamProfile(application),
          useSimpleCapacity: serverGroup.capacity.min === serverGroup.capacity.max,
          mode: mode,
        },
        organization: '',
        tag: '',
        digest: '',
        registry: '',
        repository: '',
      };

      if (serverGroup.efs) {
        command.efs = {
          mountPoint: serverGroup.efs.mountPoint,
          mountPerm: serverGroup.efs.mountPerm,
          efsId: serverGroup.efs.efsId,
        };
      } else {
        command.efs = {
          mountPerm: 'RW',
        };
      }

      if (mode !== 'editPipeline') {
        command.imageId =
          serverGroup.image.dockerImageName +
          ':' +
          (serverGroup.image.dockerImageVersion
            ? serverGroup.image.dockerImageVersion
            : serverGroup.image.dockerImageDigest);
      }

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {
      const pipelineCluster = _.cloneDeep(originalCluster);
      const commandOptions = {
        account: pipelineCluster.account,
        imageId: pipelineCluster.imageId,
        region: pipelineCluster.region,
      };

      return buildNewServerGroupCommand(application, commandOptions).then(function (command) {
        command.constraints = {
          hard:
            (originalCluster.constraints && originalCluster.constraints.hard) ||
            (originalCluster.hardConstraints &&
              originalCluster.hardConstraints.reduce((a, c) => set(a, c, 'true'), {})) ||
            {},
          soft:
            (originalCluster.constraints && originalCluster.constraints.soft) ||
            (originalCluster.softConstraints &&
              originalCluster.softConstraints.reduce((a, c) => set(a, c, 'true'), {})) ||
            {},
        };
        delete pipelineCluster.hardConstraints;
        delete pipelineCluster.softConstraints;

        const viewState = {
          disableImageSelection: true,
          useSimpleCapacity: originalCluster.capacity.min === originalCluster.capacity.max,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          defaultIamProfile: getDefaultIamProfile(application),
        };

        const viewOverrides = {
          region: pipelineCluster.region,
          credentials: pipelineCluster.account,
          iamProfile: pipelineCluster.iamProfile,
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';
        const extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);
        return extendedCommand;
      });
    }

    return {
      buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting,
      buildServerGroupCommandFromPipeline,
    };
  },
]);
