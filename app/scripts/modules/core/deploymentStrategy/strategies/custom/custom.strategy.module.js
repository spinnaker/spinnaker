'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom', [
  require('./customStrategySelector.directive.js'),
  require('./customStrategySelector.controller.js'),
  require('../../../cache/cacheInitializer.js'),
  require('../../../cache/infrastructureCaches.js'),
  require('../../../utils/timeFormatters.js'),
  require('../../../pipeline/config/services/pipelineConfigService.js'),
  require('../../../application/service/applications.read.service.js'),
])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Custom',
      description: 'Runs a custom deployment strategy',
      key: 'custom',
      providers: ['aws', 'gce'],
      additionalFields: [],
      additionalFieldsTemplateUrl: require('./additionalFields.html'),
      initializationMethod: function(command) {
      },
    });

  });
