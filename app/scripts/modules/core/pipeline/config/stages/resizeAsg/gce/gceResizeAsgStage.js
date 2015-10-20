'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.resizeAsgStage', [
  require('../../../../../application/modal/platformHealthOverride.directive.js'),
  require('./resizeAsgExecutionDetails.controller.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'resizeServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./resizeAsgStage.html'),
      executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'action', },
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
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
    stage.cloudProvider = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['GCE'];
    }

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

