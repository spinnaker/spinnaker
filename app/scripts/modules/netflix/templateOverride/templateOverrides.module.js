'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.templateOverride.templateOverrides', [
    require('../../core/templateOverride/templateOverride.registry.js'),
    require('../../core/config/settings.js'),
  ])
  .run(function(templateOverrideRegistry, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      templateOverrideRegistry.override('applicationNavHeader', require('./applicationNav.html'));
      templateOverrideRegistry.override('pipelineConfigActions', require('./pipelineConfigActions.html'));
      templateOverrideRegistry.override('spinnakerHeader', require('./spinnakerHeader.html'));
      templateOverrideRegistry.override('aws.serverGroup.securityGroups', require('../serverGroup/awsServerGroupSecurityGroups.html'));
      templateOverrideRegistry.override('aws.serverGroup.capacity', require('../serverGroup/capacity/awsServerGroupCapacity.html'));
      templateOverrideRegistry.override('aws.resize.modal', require('../serverGroup/resize/awsResizeServerGroup.html'));
    }
  }).name;
