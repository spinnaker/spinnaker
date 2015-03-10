'use strict';

angular.module('deckApp.pipelines.stage.findAmi')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Find AMI',
      description: 'Finds AMI to deploy from existing cluster',
      key: 'findAmi',
      controller: 'findAmiStageCtrl',
      controllerAs: 'findAmiStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/findAmi/findAmiStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/findAmi/findAmiExecutionDetails.html',
    });
  }).controller('findAmiStageCtrl', function($scope, stage, accountService) {
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
      accountService.getRegionsForAccount($scope.stage.account).then(function(regions) {
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

    $scope.selectionStrategies = [{
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple ASGs exist, prefer the newest'
    },{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple ASGs exist, prefer the ASG with the most instances'
    },{
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple ASGs exist, fail'
    }];

    (function() {
      if ($scope.stage.account) {
        $scope.accountUpdated();
      }
      if (!$scope.stage.selectionStrategy) {
        $scope.stage.selectionStrategy = $scope.selectionStrategies[0].val;
      }
      if (angular.isUndefined($scope.stage.onlyEnabled)) {
        $scope.stage.onlyEnabled = true;
      }
    })();

    $scope.$watch('stage.account', $scope.accountUpdated);
  });

