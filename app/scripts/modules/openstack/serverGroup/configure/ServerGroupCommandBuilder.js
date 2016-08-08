'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
])
  .factory('openstackServerGroupCommandBuilder', function ($q, openstackImageReader, settings) {

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
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: true,
            usePreferredZones: true,
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

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
    };
  });

