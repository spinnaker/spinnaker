'use strict';

angular.module('spinnaker.pipelines.stage.findAmi')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Find Image',
      description: 'Finds an image to deploy from an existing cluster',
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
      accountService.getRegionsForAccount(stage.account).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.regionsLoaded = true;
      });
    };

    $scope.toggleRegion = function(region) {
      if (!stage.regions) {
        stage.regions = [];
      }
      var idx = stage.regions.indexOf(region);
      if (idx > -1) {
        stage.regions.splice(idx,1);
      } else {
        stage.regions.push(region);
      }
    };

    $scope.selectionStrategies = [{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple server groups exist, prefer the server group with the most instances'
    },{
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple server groups exist, prefer the newest'
    },{
      label: 'Oldest',
      val: 'OLDEST',
      description: 'When multiple server groups exist, prefer the oldest'
    },{
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple server groups exist, fail'
    }];

    stage.regions = stage.regions || [];
    stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

    if (angular.isUndefined(stage.onlyEnabled)) {
      stage.onlyEnabled = true;
    }

    if (!stage.account && $scope.application.defaultCredentials) {
      stage.account = $scope.application.defaultCredentials;
    }
    if (!stage.regions.length && $scope.application.defaultRegion) {
      stage.regions.push($scope.application.defaultRegion);
    }

    if (stage.account) {
      $scope.accountUpdated();
    }

    

    $scope.$watch('stage.account', $scope.accountUpdated);
  });

