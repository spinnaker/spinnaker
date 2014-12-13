'use strict';

angular.module('deckApp.pipelines.stage.bake')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Bake',
      description: 'Bakes an AMI in the specified region',
      key: 'bake',
      controller: 'BakeStageCtrl',
      controllerAs: 'bakeStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/bake/bakeStage.html'
    });
  })
  .controller('BakeStageCtrl', function($scope, stage, bakeryService, $q, authenticationService) {
    $scope.stage = stage;

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true
    };

    $q.all({
      regions: bakeryService.getRegions(),
      baseOsOptions: bakeryService.getBaseOsOptions(),
      baseLabelOptions: bakeryService.getBaseLabelOptions()
    }).then(function(results) {
      $scope.regions = results.regions;
      $scope.baseOsOptions = results.baseOsOptions;
      $scope.baseLabelOptions = results.baseLabelOptions;
      $scope.viewState.loading = false;
    });
  });
