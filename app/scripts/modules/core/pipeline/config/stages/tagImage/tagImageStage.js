'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImageStage', [])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Tag Image',
      description: 'Tags an image',
      key: 'upsertImageTags',
      cloudProvider: 'aws',
      controller: 'TagImageStageCtrl',
      controllerAs: 'tagImageStageCtrl',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
    });
  })
  .controller('TagImageStageCtrl', function($scope) {
    $scope.stage.tags = $scope.stage.tags || {};
  });
