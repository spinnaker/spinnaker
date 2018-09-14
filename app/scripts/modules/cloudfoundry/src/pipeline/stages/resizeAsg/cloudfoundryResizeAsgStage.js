'use strict';

const angular = require('angular');

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cloudfoundry.pipeline.stage.resizeAsgStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'resizeServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./resizeAsgStage.html'),
      executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      defaultTimeoutMs: 3 * 60 * 60 * 1000 + 21 * 60 * 1000, // 3h21m
      validators: [
        {
          type: 'cfTargetImpedance',
          message:
            'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'action' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        { type: 'cfInstanceSizeField', fieldName: 'instanceCount', min: 0, preventSave: true },
        { type: 'cfInstanceSizeField', fieldName: 'memoryInMb', min: 256, preventSave: true },
        { type: 'cfInstanceSizeField', fieldName: 'diskInMb', min: 256, preventSave: true },
      ],
    });
  })
  .controller('cloudfoundryResizeAsgStageCtrl', function($scope) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    AccountService.listAccounts('cloudfoundry').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.resizeTargets = StageConstants.TARGET_LIST;

    $scope.scaleActions = [
      {
        label: 'Scale Exact',
        val: 'scale_exact',
      },
    ];

    $scope.resizeTypes = [
      {
        label: 'Percentage',
        val: 'pct',
      },
      {
        label: 'Incremental',
        val: 'incr',
      },
    ];

    stage.capacity = stage.capacity || {};
    stage.regions = stage.regions || [];
    stage.instanceCount = stage.instanceCount || 1;
    stage.memoryInMb = stage.memoryInMb || 1024;
    stage.diskInMb = stage.diskInMb || 1024;
    stage.resize_label = stage.resize_label || 'Match capacity';
    stage.resize_message = 'Scaled capacity will match the numbers entered';
    stage.target = stage.target || $scope.resizeTargets[0].val;
    stage.action = stage.action || $scope.scaleActions[0].val;
    stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;
    if (!stage.action && stage.resizeType === 'exact') {
      stage.action = 'scale_exact';
    }
    stage.cloudProvider = 'cloudfoundry';
    stage.cloudProviderType = 'cloudfoundry';

    if (
      stage.isNew &&
      $scope.application.attributes.platformHealthOnlyShowOverride &&
      $scope.application.attributes.platformHealthOnly
    ) {
      stage.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cloudfoundry) {
      stage.credentials = $scope.application.defaultCredentials.cloudfoundry;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.cloudfoundry) {
      stage.regions.push($scope.application.defaultRegions.cloudfoundry);
    }

    ctrl.updateResizeType = function() {
      if (stage.action === 'scale_exact') {
        stage.resizeType = 'exact';
        stage.resize_label = 'Match Capacity';
        stage.instanceCount = stage.instanceCount || 1;
        stage.memoryInMb = stage.memoryInMb || 1024;
        stage.diskInMb = stage.diskInMb || 1024;
        stage.resize_message = 'Scaled capacity will match the numbers entered';
      } else {
        stage.capacity = {};
        if (stage.resizeType === 'pct') {
          stage.resize_label = 'Change percent';
          stage.resize_message = 'Scaled capacity will be changed as a percentage of existing capacity';
          stage.instanceCount = stage.instanceCount || 0;
          stage.memoryInMb = stage.memoryInMb || 0;
          stage.diskInMb = stage.diskInMb || 0;
        } else {
          stage.resizeType = 'incr';
          stage.resize_label = 'Change absolute';
          stage.resize_message = 'Scaled capacity will be changed by the absolute numbers entered';
        }
      }
    };
  });
