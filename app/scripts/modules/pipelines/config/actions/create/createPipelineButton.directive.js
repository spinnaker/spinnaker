'use strict';

angular.module('deckApp.pipelines.create')
  .directive('createPipelineButton', function() {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        target: '@',
      },
      templateUrl: 'scripts/modules/pipelines/config/actions/create/createPipelineButton.html',
      controller: 'CreatePipelineButtonCtrl',
      controllerAs: 'buttonCtrl',
    };
  });
