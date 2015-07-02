'use strict';
let angular = require('angular');

module.exports =  angular.module('spinnaker.pipelines.stage.determineTargetReferenceStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      key: 'determineTargetReference',
      synthetic: true,
      executionDetailsUrl: 'app/scripts/modules/pipelines/config/stages/determineTargetReference/determineTargetReferenceDetails.html',
    });
  })
  .name;

