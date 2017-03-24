'use strict';

let angular = require('angular');

import {SETTINGS} from 'core/config/settings';

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom', [
  require('./customStrategySelector.directive.js'),
  require('./customStrategySelector.controller.js'),
  require('core/utils/timeFormatters.js')
])
  .config(function(deploymentStrategyConfigProvider) {
    if (SETTINGS.feature.pipelines !== false) {
      deploymentStrategyConfigProvider.registerStrategy({
        label: 'Custom',
        description: 'Runs a custom deployment strategy',
        key: 'custom',
        providers: ['aws', 'gce', 'titus', 'kubernetes', 'appengine'],
        additionalFields: [],
        additionalFieldsTemplateUrl: require('./additionalFields.html'),
        initializationMethod: angular.noop,
      });
    }

  });
