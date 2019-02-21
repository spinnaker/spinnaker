'use strict';

import _ from 'lodash';

const angular = require('angular');

import { ApplicationReader, NameUtils, SubnetReader } from '@spinnaker/core';

import { OpenStackProviderSettings } from '../../openstack.settings';

module.exports = angular
  .module('spinnaker.openstack.serverGroupCommandBuilder.service', [require('../../image/image.reader').name])
  .factory('openstackServerGroupCommandBuilder', [
    '$q',
    'openstackImageReader',
    'loadBalancerReader',
    function($q, openstackImageReader, loadBalancerReader) {
      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};

        var defaultCredentials =
          defaults.account || application.defaultCredentials.openstack || OpenStackProviderSettings.defaults.account;
        var defaultRegion =
          defaults.region || application.defaultRegions.openstack || OpenStackProviderSettings.defaults.region;

        return $q.when({
          selectedProvider: 'openstack',
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
          associatePublicIpAddress: false,
          strategy: '',
          stack: '',
          freeFormDetails: '',
          minSize: 1,
          desiredSize: 1,
          maxSize: 1,
          loadBalancers: [],
          securityGroups: [],
          tags: {},
          viewState: {
            mode: defaults.mode || 'create',
            disableStrategySelection: true,
            loadBalancersConfigured: false,
            securityGroupsConfigured: false,
          },
        });
      }

      // Only used to prepare view requiring template selecting
      function buildNewServerGroupCommandForPipeline(stage, pipeline) {
        return ApplicationReader.getApplication(pipeline.application).then(function(application) {
          return buildNewServerGroupCommand(application).then(function(command) {
            command.viewState.requiresTemplateSelection = true;
            command.viewState.disableStrategySelection = false;
            return command;
          });
        });
      }

      function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
        var subnetsLoader = SubnetReader.listSubnetsByProvider('openstack');
        var serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);
        var asyncLoader = $q.all({
          subnets: subnetsLoader,
          loadBalancers: loadBalancerReader.listLoadBalancers('openstack'),
        });

        return asyncLoader.then(function(asyncData) {
          var loadBalancers = {};
          _.forEach(asyncData.loadBalancers, lb => {
            loadBalancers[lb.name] = lb.id;
          });

          var command = {
            application: application.name,
            stack: serverGroupName.stack,
            freeFormDetails: serverGroupName.freeFormDetails,
            credentials: serverGroup.account,
            loadBalancers: serverGroup.loadBalancers.map(lbName =>
              /^openstack:/.test(lbName) ? lbName.split(':')[4] : loadBalancers[lbName],
            ),
            region: serverGroup.region,
            minSize: parseInt(serverGroup.scalingConfig.minSize),
            maxSize: parseInt(serverGroup.scalingConfig.maxSize),
            desiredSize: parseInt(serverGroup.scalingConfig.desiredSize),
            image: serverGroup.image.id,
            instanceType: serverGroup.launchConfig.instanceType,
            userDataType: serverGroup.advancedConfig.userDataType,
            userData: serverGroup.advancedConfig.userData,
            tags: serverGroup.tags,
            selectedProvider: 'openstack',
            source: {
              account: serverGroup.account,
              region: serverGroup.region,
              asgName: serverGroup.name,
              serverGroupName: serverGroup.name,
            },
            viewState: {
              mode: mode,
              isNew: false,
              dirty: {},
            },
          };

          if (mode === 'editPipeline') {
            command.strategy = 'redblack';
            command.suspendedProcesses = [];
            delete command.image;
          }

          command.subnetId = serverGroup.subnetId;
          command.subnet = _.chain(asyncData.subnets)
            .find({ id: serverGroup.subnetId })
            .valueOf();

          if (serverGroup.launchConfig) {
            angular.extend(command, {
              instanceType: serverGroup.launchConfig.instanceType,
              associatePublicIpAddress: serverGroup.launchConfig.associatePublicIpAddress,
              floatingNetworkId: serverGroup.launchConfig.floatingNetworkId,
            });

            command.securityGroups = serverGroup.launchConfig.securityGroups || [];
          }

          return command;
        });
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster) {
        var command = _.cloneDeep(originalCluster);
        if (!command.credentials) {
          command.credentials = command.account;
        }
        var params = command.serverGroupParameters;
        delete command.serverGroupParameters;
        return _.extend(command, params, {
          selectedProvider: 'openstack',
          viewState: {
            disableImageSelection: true,
            mode: 'editPipeline',
            dirty: {},
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
