'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTagsStage', [
  require('../bake/bakery.service.js'),
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Find Image from Tags',
      description: 'Finds an image to deploy from existing tags',
      key: 'findImageFromTags',
      cloudProvider: 'aws',
      controller: 'FindImageFromTagsStageCtrl',
      controllerAs: 'findImageFromTagsStageCtrl',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'packageName', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'tags', },
      ],
    });
  })
  .controller('FindImageFromTagsStageCtrl', function($scope, bakeryService) {
    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.regions = $scope.stage.regions || [];
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';

    bakeryService.getRegions('aws').then(function(regions) {
      $scope.regions = regions;
    });
  });
