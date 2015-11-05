'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.templateOverride.templateOverrides', [
    require('../../core/templateOverride/templateOverride.registry.js'),
  ])
  .config(function(templateOverrideRegistryProvider) {
    templateOverrideRegistryProvider.override('applicationNavHeader', require('./applicationNav.html'));
    templateOverrideRegistryProvider.override('pipelineConfigActions', require('./pipelineConfigActions.html'));
  }).name;
