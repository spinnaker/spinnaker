'use strict';

const angular = require('angular');

import { BakeryReader, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.pipeline.stage.findImageFromTagsStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'oracle',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'packageName' },
      ],
    });
  })
  .controller('oracleFindImageFromTagsStageCtrl', ['$scope', function($scope) {
    $scope.stage.packageName = $scope.stage.packageName || '*';
    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.regions = $scope.stage.regions || [];
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'oracle';

    BakeryReader.getRegions('oracle').then(function(regions) {
      $scope.regions = regions;
    });
  }]);
