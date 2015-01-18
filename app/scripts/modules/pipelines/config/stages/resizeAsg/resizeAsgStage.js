'use strict';

angular.module('deckApp.pipelines.stage.resizeAsg')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Resize ASG',
      description: 'Resizes an ASG',
      key: 'resizeAsg',
      controller: 'ResizeAsgStageCtrl',
      controlelrAs: 'resizeAsgStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/resizeAsg/resizeAsgStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/resizeAsg/resizeAsgExecutionDetails.html',
    });
  }).controller('ResizeAsgStageCtrl', function($scope, stage, accountService) {
    $scope.stage = stage;

    $scope.state = {
      accounts: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];
    $scope.regionsLoaded = false;

    $scope.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.regionsLoaded = true;
      });
    };

    $scope.toggleRegion = function(region) {
      var idx = $scope.stage.regions.indexOf(region);
      if (idx > -1) {
        $scope.stage.regions.splice(idx,1);
      } else {
        $scope.stage.regions.push(region);
      }
    };

    $scope.resizeTargets = [
      {
        label: 'Last ASG',
        val: 'ancestor_asg'
      },
      {
        label: 'Current ASG',
        val: 'current_asg'
      }
    ];

    $scope.scaleActions = [
      {
        label: 'Scale Up',
        val: 'scale_up',
      },
      {
        label: 'Scale Down',
        val: 'scale_down'
      }
    ];

    $scope.resizeTypes = [
      {
        label: 'Percentage',
        val: 'pct'
      },
      {
        label: 'Incremental',
        val: 'incr'
      },
      {
        label: 'Exact',
        val: 'exact'
      }
    ];

    (function() {
      if (!$scope.stage.capacity) {
        $scope.stage.capacity = {};
      }
      if (!$scope.stage.regions) {
        $scope.stage.regions = [];
      }
      if ($scope.stage.credentials) {
        $scope.accountUpdated();
      }
      if ($scope.stage.target) {
        $scope.resizeTarget = _.groupBy($scope.resizeTargets, 'val')[$scope.stage.target][0];
      } else {
        $scope.resizeTarget = $scope.resizeTargets[0];
        $scope.stage.target = $scope.resizeTarget.val;
      }
      if ($scope.stage.action) {
        $scope.scaleAction = _.groupBy($scope.scaleActions, 'val')[$scope.stage.action][0];
      } else {
        $scope.scaleAction = $scope.scaleActions[0];
        $scope.stage.action = $scope.scaleAction.val;
      }
      if ($scope.stage.resizeType) {
        $scope.resizeType = _.groupBy($scope.resizeTypes, 'val')[$scope.stage.resizeType][0];
      } else {
        $scope.resizeType = $scope.resizeTypes[0];
        $scope.stage.resizeType = $scope.resizeType.val; 
      }
    })();
  

    function updateCapacity() {
      $scope.stage.capacity.desired = $scope.stage.capacity.max;
    }

    $scope.updateResizeTarget = function(type) {
      $scope.resizeTarget = type;
      $scope.stage.target = type.val;
    };

    $scope.updateScaleAction = function(type) {
      $scope.scaleAction = type;
      $scope.stage.action = type.val;
    };

    $scope.updateResizeType = function(type) {
      $scope.stage.capacity = {};
      delete $scope.stage.scalePct;
      delete $scope.stage.scaleNum;
      $scope.resizeType = type;
      $scope.stage.resizeType = type.val;
    };

    $scope.$watch('stage.capacity.max', updateCapacity);
  });

