'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create.CreatePipelineButtonCtrl', [
])
  .controller('CreatePipelineButtonCtrl', function($scope, $uibModal) {
    this.createPipeline = function() {
      $uibModal.open({
        templateUrl: require('./createPipelineModal.html'),
        controller: 'CreatePipelineModalCtrl',
        controllerAs: 'createPipelineModalCtrl',
        resolve: {
          application: function() { return $scope.application; },
          target: function() { return $scope.target; },
          reinitialize: function() { return $scope.reinitialize; },
        }
      });
    };
  }).name;
