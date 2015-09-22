'use strict';

let angular = require('angular');

module.exports =  angular.module('spinnaker.pipelines.stage.determineTargetReferenceStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      key: 'determineTargetServerGroup',
      alias: 'determineTargetReference',
      synthetic: true,
      executionDetailsUrl: require('./determineTargetReferenceDetails.html'),
    });
  })
  .name;

