'use strict';

let angular = require('angular');

require('./findAmiStage.html');
require('./findAmiExecutionDetails.html');

//BEN_TODO
module.exports = angular.module('spinnaker.pipelines.stage.findAmiStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Find Image',
      description: 'Finds an image to deploy from an existing cluster',
      key: 'findAmi',
      controller: 'findAmiStageCtrl',
      controllerAs: 'findAmiStageCtrl',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
    });
  }).controller('findAmiStageCtrl', function($scope, stage, accountService, _) {
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
        $scope.stage.regions.splice(idx, 1);
      } else {
        $scope.stage.regions.push(region);
      }
    };

    $scope.selectionStrategies = [{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple server groups exist, prefer the server group with the most instances'
    }, {
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple server groups exist, prefer the newest'
    }, {
      label: 'Oldest',
      val: 'OLDEST',
      description: 'When multiple server groups exist, prefer the oldest'
    }, {
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple server groups exist, fail'
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
  })
  .name;

