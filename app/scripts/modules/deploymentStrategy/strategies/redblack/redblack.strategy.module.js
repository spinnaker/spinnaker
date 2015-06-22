'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deploymentStrategy.redblack', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Red/Black',
      description: 'Disables previous server group as soon as new server group passes health checks',
      key: 'redblack',
      additionalFields: ['scaleDown', 'maxRemainingAsgs'],
      additionalFieldsTemplateUrl: 'scripts/modules/deploymentStrategy/strategies/redblack/additionalFields.html',
    });
  });
