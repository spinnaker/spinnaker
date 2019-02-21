'use strict';

const angular = require('angular');

import { BakeryReader, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.pipeline.stage.findImageFromTagsStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'aws',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'packageName' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'tags' },
      ],
    });
  })
  .controller('awsFindImageFromTagsStageCtrl', [
    '$scope',
    function($scope) {
      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.regions = $scope.stage.regions || [];
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';

      BakeryReader.getRegions('aws').then(function(regions) {
        $scope.regions = regions;
      });
    },
  ]);
