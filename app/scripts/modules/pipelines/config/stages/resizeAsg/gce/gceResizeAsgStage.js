'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.gce.resizeAsgStage', [
  require('./resizeAsgExecutionDetails.controller.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'resizeAsg',
      cloudProvider: 'gce',
      key: 'resizeAsg_gce',
      templateUrl: require('./resizeAsgStage.html'),
      executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.'
        },
      ],
    });
  }).controller('gceResizeAsgStageCtrl', function($scope, accountService, stageConstants, _) {

    var ctrl = this;

    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
      zonesLoaded: false,
      loading: true,
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
      $scope.viewState.loading = false;
    });

    $scope.zones = ['us-central1-a', 'us-central1-b', 'us-central1-c'];

    ctrl.accountUpdated = function () {
      if (!$scope.accounts) {
        return;
      }
      $scope.selectedAccount = _.find($scope.accounts, function (candidate) {
        return candidate.name === stage.credentials;
      });
      accountService.getRegionsForAccount(stage.credentials).then(function (regions) {
        if ($scope.selectedAccount) {
          stage.cloudProviderType = 'gce';
          $scope.zones = _.flatten(_.map(regions, function(val) { return val; } ));
        }
        $scope.zonesLoaded = true;
      });
    };

    $scope.resizeTargets =  stageConstants.targetList;

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

    stage.capacity = stage.capacity || {};
    stage.zones = stage.zones || [];
    stage.target = stage.target || $scope.resizeTargets[0].val;
    stage.action = stage.action || $scope.scaleActions[0].val;
    stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;

    if (!stage.credentials && $scope.application.defaultCredentials) {
      stage.credentials = $scope.application.defaultCredentials;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }

    ctrl.updateResizeType = function() {
      stage.capacity = {};
      delete stage.scalePct;
      delete stage.scaleNum;
    };

  })
  .name;

