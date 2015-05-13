'use strict';

angular.module('deckApp.pipelines.stage.enableAsg')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Enable Server Group',
      description: 'Enables a server group',
      key: 'enableAsg',
      controller: 'EnableAsgStageCtrl',
      controllerAs: 'enableAsgStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/enableAsg/enableAsgStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/enableAsg/enableAsgExecutionDetails.html',
    });
  }).controller('EnableAsgStageCtrl', function($scope, stage, accountService) {
    $scope.stage = stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

    $scope.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.regionsLoaded = true;
      });
    };

    $scope.toggleRegion = function(region) {
      if (!$scope.stage.regions) {
        $scope.stage.regions = [];
      }
      var idx = $scope.stage.regions.indexOf(region);
      if (idx > -1) {
        $scope.stage.regions.splice(idx,1);
      } else {
        $scope.stage.regions.push(region);
      }
    };

    $scope.targets = [
      {
        label: 'Current Server Group',
        val: 'current_asg'
      },
      {
        label: 'Last Server Group',
        val: 'ancestor_asg'
      }
    ];

    (function() {
      if ($scope.stage.credentials) {
        $scope.accountUpdated();
      }
      if (!$scope.stage.target) {
        $scope.stage.target = $scope.targets[0].val;
      }
    })();

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

