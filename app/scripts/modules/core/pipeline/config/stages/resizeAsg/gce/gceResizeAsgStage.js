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
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'action' },
        { type: 'requiredField', fieldName: 'zones' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'}
      ],
    });
  }).controller('gceResizeAsgStageCtrl', function($scope, accountService, stageConstants, _) {

    var ctrl = this;

    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
      zonesLoaded: false
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    $scope.zones = {"us-central1": ['us-central1-a', 'us-central1-b', 'us-central1-c']};

    ctrl.accountUpdated = function () {
      accountService.getRegionsForAccount(stage.credentials).then(function(zoneMap) {
        $scope.zones = zoneMap;
        $scope.zonesLoaded = true;
      });
    };

    $scope.resizeTargets =  stageConstants.targetList;

    $scope.scaleActions = [
      {
        label: 'Scale Up',
        val: 'scale_up'
      },
      {
        label: 'Scale Down',
        val: 'scale_down'
      },
      {
        label: 'Scale to Cluster Size',
        val: 'scale_to_cluster'
      },
      {
        label: 'Scale to Exact Size',
        val: 'scale_exact'
      },
    ];

    $scope.resizeTypes = [
      {
        label: 'Percentage',
        val: 'pct'
      },
      {
        label: 'Incremental',
        val: 'incr'
      }
    ];

    stage.capacity = stage.capacity || {};
    stage.zones = stage.zones || [];
    stage.target = stage.target || $scope.resizeTargets[0].val;
    stage.action = stage.action || $scope.scaleActions[0].val;
    stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;
    if (stage.resizeType === 'exact') {
      stage.action = 'scale_exact';
    }
    stage.cloudProvider = 'gce';
    stage.cloudProviderType = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }

    ctrl.updateResizeType = function() {
      if (stage.action === 'scale_exact') {
        stage.resizeType = 'exact';
        delete stage.scalePct;
        delete stage.scaleNum;
      } else {
        stage.capacity = {};
        if (stage.resizeType === 'pct') {
          delete stage.scaleNum;
        } else if (stage.resizeType === 'incr') {
          delete stage.scalePct;
        }
      }
    };
  });

