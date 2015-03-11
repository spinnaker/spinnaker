'use strict';

angular.module('deckApp.pipelines.stage.destroyAsg')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Destroy ASG',
      description: 'Destroys an ASG',
      key: 'destroyAsg',
      controller: 'DestroyAsgStageCtrl',
      controllerAs: 'destroyAsgStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/destroyAsg/destroyAsgStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/destroyAsg/destroyAsgExecutionDetails.html',
    });
  }).controller('DestroyAsgStageCtrl', function($scope, stage, accountService) {
    var ctrl = this;

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

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.state.regionsLoaded = true;
      });
    };

    ctrl.toggleRegion = function(region) {
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
        label: 'Last ASG',
        val: 'ancestor_asg'
      },
      {
        label: 'Current ASG',
        val: 'current_asg'
      }
    ];

    (function() {
      if ($scope.stage.credentials) {
        ctrl.accountUpdated();
      }
      if (!$scope.stage.target) {
        $scope.stage.target = $scope.targets[0].val;
      }
    })();

  });

