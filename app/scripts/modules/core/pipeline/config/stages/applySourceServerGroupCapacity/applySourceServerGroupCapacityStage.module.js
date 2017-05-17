'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage', [
  require('./applySourceServerGroupCapacityDetails.controller.js'),
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'applySourceServerGroupCapacity',
      executionDetailsUrl: require('./applySourceServerGroupCapacityDetails.html'),
    });
  });
