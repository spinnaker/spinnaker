'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.titus.serverGroupCommandBuilder.service', [
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
])
  .factory('titusServerGroupCommandBuilder', function (settings, $q,
                                                       accountService, namingService) {
    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || settings.providers.titus.defaults.account;
      var defaultRegion = defaults.region || settings.providers.titus.defaults.region;
      var defaultZone = defaults.zone || settings.providers.titus.defaults.zone;
      var defaultIamProfile = settings.providers.titus.defaults.iamProfile || '{{application}}InstanceProfile';
      defaultIamProfile = defaultIamProfile.replace('{{application}}', application.name);

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        zone: defaultZone,
        network: 'default',
        inService: true,
        resources: {
          allocateIpAddress: true,
          cpu: 1,
          networkMbps: 128,
          disk: 512,
          memory: 512
        },
        efs: {
          mountPerm: 'RW'
        },
        strategy: '',
        capacity: {
          min: 1,
          max: 1,
          desired: 1
        },
        env: {},
        labels: {},
        cloudProvider: 'titus',
        selectedProvider: 'titus',
        iamProfile: defaultIamProfile,
        softConstraints: [],
        hardConstraints: [],
        viewState: {
          useSimpleCapacity: true,
          usePreferredZones: true,
          mode: defaults.mode || 'create',
        },
        securityGroups: [],
      };

      return $q.when(command);
    }

    // Only used to prepare view requiring template selecting
    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        }
      });
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);

      var command = {
        application: application.name,
        strategy: '',
        stack: serverGroupName.stack,
        freeFormDetails: serverGroupName.freeFormDetails,
        account: serverGroup.account,
        credentials: serverGroup.account,
        region: serverGroup.region,
        env: serverGroup.env,
        labels: serverGroup.labels,
        entryPoint: serverGroup.entryPoint,
        iamProfile: serverGroup.iamProfile || application.name + 'InstanceProfile',
        capacityGroup: serverGroup.capacityGroup,
        securityGroups: serverGroup.securityGroups || [],
        hardConstraints: (serverGroup.hardConstraints || []),
        softConstraints: (serverGroup.softConstraints || []),
        inService: serverGroup.disabled ? false : true,
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          asgName: serverGroup.name,
        },
        resources: {
          cpu: serverGroup.resources.cpu,
          memory: serverGroup.resources.memory,
          disk: serverGroup.resources.disk,
          networkMbps: serverGroup.resources.networkMbps,
          ports: serverGroup.resources.ports,
          allocateIpAddress: serverGroup.resources.allocateIpAddress,
        },
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired
        },
        cloudProvider: 'titus',
        selectedProvider: 'titus',
        viewState: {
          useSimpleCapacity: true,
          mode: mode,
        },
      };

      if (serverGroup.efs) {
        command.efs = {
          mountPoint: serverGroup.efs.mountPoint,
          mountPerm: serverGroup.efs.mountPerm,
          efsId: serverGroup.efs.efsId
        };
      } else {
        command.efs = {
          mountPerm: 'RW'
        };
      }

      if (mode !== 'editPipeline') {
        command.imageId = serverGroup.image.dockerImageName + ':' + serverGroup.image.dockerImageVersion;
      }

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var commandOptions = {account: pipelineCluster.account, region: pipelineCluster.region};
      var asyncLoader = $q.all({command: buildNewServerGroupCommand(application, commandOptions)});

      return asyncLoader.then(function (asyncData) {
        var command = asyncData.command;
        command.hardConstraints = (command.hardConstraints || []);
        command.softConstraints = (command.softConstraints || []);

        var viewState = {
          disableImageSelection: true,
          useSimpleCapacity: true,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
        };

        var viewOverrides = {
          region: pipelineCluster.region,
          credentials: pipelineCluster.account,
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';
        var extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);
        return extendedCommand;
      });

    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
  });

