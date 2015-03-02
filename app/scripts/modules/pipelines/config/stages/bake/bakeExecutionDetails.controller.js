'use strict';

angular.module('deckApp.pipelines.stage.bake.executionDetails.controller', [])
  .controller('BakeExecutionDetailsCtrl', function ($scope) {
    $scope.provider = $scope.stage.context.cloudProviderType || 'aws';
  });
