'use strict';

const angular = require('angular');

import { Registry } from '@spinnaker/core';

module.exports = angular.module('spinnaker.canary.canaryDeploymentStage', []).config(function() {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'canaryDeployment',
    executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
  });
});
