'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.tagImageStage', [
  require('core/pipeline/config/pipelineConfigProvider.js'),
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
