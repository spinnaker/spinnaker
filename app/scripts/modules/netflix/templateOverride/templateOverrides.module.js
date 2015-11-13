'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.templateOverride.templateOverrides', [
    require('../../core/templateOverride/templateOverride.registry.js'),
  ])
  .config(function(templateOverrideRegistryProvider) {
    templateOverrideRegistryProvider.override('applicationNavHeader', require('./applicationNav.html'));
    templateOverrideRegistryProvider.override('pipelineConfigActions', require('./pipelineConfigActions.html'));
    templateOverrideRegistryProvider.override('spinnakerHeader', require('./spinnakerHeader.html'));
    templateOverrideRegistryProvider.override('aws.serverGroup.securityGroups', require('../serverGroup/awsServerGroupSecurityGroups.html'));
    templateOverrideRegistryProvider.override('aws.serverGroup.capacity', require('../serverGroup/capacity/awsServerGroupCapacity.html'));
    templateOverrideRegistryProvider.override('aws.resize.modal', require('../serverGroup/resize/awsResizeServerGroup.html'));
  }).name;
