'use strict';
let angular = require('angular');

require('./determineTargetReferenceDetails.html');

module.exports =  angular.module('spinnaker.pipelines.stage.determineTargetReferenceStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      key: 'determineTargetReference',
      synthetic: true,
      executionDetailsUrl: require('./determineTargetReferenceDetails.html'),
    });
  })
  .name;

