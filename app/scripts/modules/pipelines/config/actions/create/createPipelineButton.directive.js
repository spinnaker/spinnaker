'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.create')
  .directive('createPipelineButton', function() {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        target: '@',
        reinitialize: '&',
      },
      templateUrl: require('./createPipelineButton.html'),
      controller: 'CreatePipelineButtonCtrl',
      controllerAs: 'buttonCtrl',
    };
  });
