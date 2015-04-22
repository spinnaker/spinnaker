'use strict';

angular.module('deckApp.pipelines.stage.bake')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      key: 'bake',
      controller: 'BakeStageCtrl',
      controllerAs: 'bakeStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/bake/bakeStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/bake/bakeExecutionDetails.html',
      validators: [
        {
          type: 'requiredField',
          fieldName: 'package',
          message: '<strong>Package</strong> is a required field on bake stages.',
        },
      ],
    });
  })
  .controller('BakeStageCtrl', function($scope, stage, bakeryService, $q, authenticationService, accountService) {
    var ctrl = this;

    $scope.stage = stage;

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true,
      providerSelected: !!$scope.stage.cloudProviderType,
      providerSelectionRequired: true,
    };

    accountService.listProviders().then(function(providers) {
      if (providers.length > 1) {
        $scope.viewState.providerSelectionRequired = true;
        $scope.viewState.loading = false;
      } else {
        $scope.viewState.providerSelectionRequired = false;
      }
      ctrl.providerSelected();
    });

    this.providerSelected = function() {
      $scope.viewState.providerSelected = true;
      $q.all({
        regions: bakeryService.getRegions($scope.stage.cloudProviderType),
        baseOsOptions: bakeryService.getBaseOsOptions(),
        baseLabelOptions: bakeryService.getBaseLabelOptions(),
        vmTypes: bakeryService.getVmTypes(),
        storeTypes: bakeryService.getStoreTypes(),
      }).then(function(results) {
        if (!$scope.stage.cloudProviderType || $scope.stage.cloudProviderType === 'aws') {
          $scope.regions = results.regions;
        } else {
          $scope.regions  = ['global'];
        }
        if ($scope.regions.length === 1) {
          $scope.stage.region = $scope.regions[0];
        } else if ($scope.regions.indexOf($scope.stage.region) === -1) {
          delete $scope.stage.region;
        }
        $scope.baseOsOptions = results.baseOsOptions;
        $scope.vmTypes = results.vmTypes;
        $scope.baseLabelOptions = results.baseLabelOptions;
        $scope.storeTypes = results.storeTypes;

        if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
          $scope.stage.baseOs = $scope.baseOsOptions[0];
        }
        if (!$scope.stage.baseLabel && $scope.baseLabelOptions && $scope.baseLabelOptions.length) {
          $scope.stage.baseLabel = $scope.baseLabelOptions[0];
        }
        if (!$scope.stage.vmType && $scope.vmTypes && $scope.vmTypes.length) {
          $scope.stage.vmType = $scope.vmTypes[0];
        }
        if (!$scope.stage.storeType && $scope.storeTypes && $scope.storeTypes.length) {
          $scope.stage.storeType = $scope.storeTypes[0];
        }
        $scope.viewState.loading = false;
      });
    };

    $scope.$watch('stage.cloudProviderType', this.providerSelected);

  });
