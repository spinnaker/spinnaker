'use strict';

const angular = require('angular');

import { Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.pipeline.stage.findImageFromTagsStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'ecs',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
    });
  })
  .controller('ecsFindImageFromTagsStageCtrl', ['$scope', function($scope) {
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'ecs';
  }]);
