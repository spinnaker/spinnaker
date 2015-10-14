'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.resizeAsgStage', [
  require('./resizeAsgExecutionDetails.controller.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'resizeServerGroup',
      alias: 'resizeAsg',
      cloudProvider: 'cf',
      templateUrl: require('./resizeAsgStage.html'),
      executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster', },
      ],
    });
  }).controller('cfResizeAsgStageCtrl', function($scope, accountService, stageConstants, _) {

    var ctrl = this;

    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
      regionsLoaded: false,
    };

    accountService.listAccounts('cf').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

    ctrl.accountUpdated = function() {
      $scope.viewState.regionsLoaded = false;
      accountService.getRegionsForAccount(stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.viewState.regionsLoaded = true;
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
    stage.regions = stage.regions || [];
    stage.target = stage.target || $scope.resizeTargets[0].val;
    stage.action = stage.action || $scope.scaleActions[0].val;
    stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;
    stage.cloudProvider = 'cf';

    if (!stage.credentials && $scope.application.defaultCredentials) {
      stage.credentials = $scope.application.defaultCredentials;
    }
    if (!stage.regions.length && $scope.application.defaultRegion) {
      stage.regions.push($scope.application.defaultRegion);
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

