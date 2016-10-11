'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom', [
  require('./customStrategySelector.directive.js'),
  require('./customStrategySelector.controller.js'),
  require('core/cache/cacheInitializer.js'),
  require('core/cache/infrastructureCaches.js'),
  require('core/utils/timeFormatters.js'),
  require('core/pipeline/config/services/pipelineConfigService.js'),
  require('core/application/service/applications.read.service.js'),
  require('core/config/settings.js'),
])
  .config(function(deploymentStrategyConfigProvider, settings) {
    if (settings.feature && settings.feature.pipelines !== false) {
      deploymentStrategyConfigProvider.registerStrategy({
        label: 'Custom',
        description: 'Runs a custom deployment strategy',
        key: 'custom',
        providers: ['aws', 'gce', 'titus'],
        additionalFields: [],
        additionalFieldsTemplateUrl: require('./additionalFields.html'),
        initializationMethod: angular.noop,
      });
    }

  });
