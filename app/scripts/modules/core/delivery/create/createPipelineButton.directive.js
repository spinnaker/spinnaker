'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create.createPipelineButtonDirective', [
])
  .directive('createPipelineButton', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        application: '=',
      },
      templateUrl: require('./createPipelineButton.html'),
      controller: 'CreatePipelineButtonCtrl',
      controllerAs: 'buttonCtrl',
      replace: true,
    };
  });
