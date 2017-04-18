'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.tagImageStage', [
  PIPELINE_CONFIG_PROVIDER,
  require('./tagImageExecutionDetails.controller.js'),
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'aws',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
    });
  })
  .controller('awsTagImageStageCtrl', function($scope) {
    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';
  });
