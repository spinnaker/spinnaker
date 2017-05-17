'use strict';

const angular = require('angular');

import {SETTINGS} from 'core/config/settings';
import {TIME_FORMATTERS} from 'core/utils/timeFormatters';

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom', [
  require('./customStrategySelector.directive.js'),
  require('./customStrategySelector.controller.js'),
  TIME_FORMATTERS
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
