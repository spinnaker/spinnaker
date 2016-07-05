'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.redblack', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Red/Black',
      description: 'Disables previous server group as soon as new server group passes health checks',
      key: 'redblack',
      providers: ['aws', 'gce', 'cf', 'kubernetes', 'titan'],
      additionalFields: ['scaleDown', 'maxRemainingAsgs'],
      additionalFieldsTemplateUrl: require('./additionalFields.html'),
    });
  });
