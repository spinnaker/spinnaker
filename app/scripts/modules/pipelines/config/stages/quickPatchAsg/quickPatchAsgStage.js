'use strict';

angular.module('deckApp.pipelines.stage.quickPatchAsg')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Quick Patch ASG',
      description: 'Quick Patches an ASG',
      key: 'quickPatch',
      controller: 'QuickPatchAsgStageCtrl',
      controllerAs: 'QuickPatchAsgStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/quickPatchAsg/quickPatchAsgStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/quickPatchAsg/quickPatchAsgExecutionDetails.html'
    });
  }).controller('QuickPatchAsgStageCtrl', function($scope, stage, bakeryService, accountService) {
    $scope.stage = stage;
    $scope.baseOsOptions = ['ubuntu', 'centos'];
    $scope.stage.application = $scope.application.name;
    $scope.stage.healthProviders = ['Discovery'];

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.regionsLoaded = true;
        $scope.stage.account = $scope.stage.credentials;
      });
    };

    (function() {
      if ($scope.stage.credentials) {
        $scope.accountUpdated();
      }
    })();

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

