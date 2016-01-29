'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.disableAsgStage', [
  require('../../../../../application/modal/platformHealthOverride.directive.js'),
  require('../../../../../../core/application/listExtractor/listExtractor.service'),
  require('../../stageConstants.js'),
  require('../../../../../account/account.service.js'),
  require('./disableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('./disableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('gceDisableAsgStageCtrl', function($scope, accountService, stageConstants, appListExtractorService) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      zonesLoaded: false
    };

    let setClusterList = () => {
      let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndZone($scope.stage.credentials, $scope.stage.zones);
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

    ctrl.resetSelectedCluster = () => {
      $scope.stage.cluster = undefined;
      setClusterList();
    };

    $scope.zones = {'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-c']};

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(zoneMap) {
        $scope.zones = zoneMap;
        $scope.zonesLoaded = true;
      });
    };

    ctrl.reset = () => {
      ctrl.accountUpdated();
      ctrl.resetSelectedCluster();
    };

    $scope.targets = stageConstants.targetList;

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  });

