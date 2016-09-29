'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
])
  .factory('openstackServerGroupCommandBuilder', function ($q, openstackImageReader, subnetReader, loadBalancerReader, settings, namingService) {

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || application.defaultCredentials.openstack || settings.providers.openstack.defaults.account;
      var defaultRegion = defaults.region || application.defaultRegions.openstack || settings.providers.openstack.defaults.region;

      return $q.when({
          selectedProvider: 'openstack',
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
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
    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        }
      });
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
      var subnetsLoader = subnetReader.listSubnets();
      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);
      // TODO: unused variable
      // var instanceType = serverGroup.launchConfig ? serverGroup.launchConfig.instanceType : null;

      var asyncLoader = $q.all({
        subnets: subnetsLoader,
        loadBalancers: loadBalancerReader.listLoadBalancers('openstack')
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
          loadBalancers: serverGroup.loadBalancers.map((lbName) => loadBalancers[lbName]),
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
            serverGroupName: serverGroup.name
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
        }

        command.subnetId = serverGroup.subnetId;
        command.subnet = _.chain(asyncData.subnets).find({'id': serverGroup.subnetId});

        if (serverGroup.launchConfig) {
          angular.extend(command, {
            instanceType: serverGroup.launchConfig.instanceType,
            associatePublicIpAddress: serverGroup.launchConfig.associatePublicIpAddress,
            ramdiskId: serverGroup.launchConfig.ramdiskId,
          });

          command.securityGroups = serverGroup.launchConfig.securityGroups || [];
        }

        return command;
      });
    }


    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
    };
  });

