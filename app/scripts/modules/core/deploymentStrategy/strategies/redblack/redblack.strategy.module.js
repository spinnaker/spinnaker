'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.redblack', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Red/Black',
      description: 'Disables <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
      key: 'redblack',
      providers: ['aws', 'gce', 'cf', 'kubernetes', 'titus', 'openstack'],
      additionalFields: ['scaleDown', 'maxRemainingAsgs'],
      additionalFieldsTemplateUrl: require('./additionalFields.html'),
    });
  });
