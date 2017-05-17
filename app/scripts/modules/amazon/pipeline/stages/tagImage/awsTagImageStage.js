'use strict';

const angular = require('angular');

import { PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.tagImageStage', [
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'aws',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
      executionConfigSections: ['tagImageConfig', 'taskStatus'],
    });
  })
  .controller('awsTagImageStageCtrl', function($scope) {
    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';
  });
