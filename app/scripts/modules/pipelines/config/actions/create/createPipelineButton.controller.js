'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.create')
  .controller('CreatePipelineButtonCtrl', function($scope, $modal) {
    this.createPipeline = function() {
      $modal.open({
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
  });
