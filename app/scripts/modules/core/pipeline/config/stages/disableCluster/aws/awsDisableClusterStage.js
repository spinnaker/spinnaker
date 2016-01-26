'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.disableClusterStage', [
  require('../../../../../../core/application/listExtractor/listExtractor.service'),
  require('../../stageConstants.js'),
  require('./disableClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'aws',
      templateUrl: require('./disableClusterStage.html'),
      executionDetailsUrl: require('./disableClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('awsDisableClusterStageCtrl', function($scope, accountService, stageConstants, appListExtractorService) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    let setClusterList = () => {
      let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion($scope.stage.credentials, $scope.stage.regions);
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    ctrl.resetSelectedCluster = () => {
      $scope.stage.cluster = undefined;
      setClusterList();
    };

    accountService.listAccounts('aws').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

    accountService.listAccounts('aws').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

    ctrl.accountUpdated = function() {
      let accountFilter = (cluster) => cluster.account === $scope.stage.credentials;
      $scope.regions = appListExtractorService.getRegions([$scope.application], accountFilter);
      $scope.state.regionsLoaded = true;
    };

    ctrl.reset = () => {
      ctrl.accountUpdated();
      ctrl.resetSelectedCluster();
    };

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'aws';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Amazon'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.aws) {
      stage.credentials = $scope.application.defaultCredentials.aws;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.aws) {
      stage.regions.push($scope.application.defaultRegions.aws);
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }

    if (stage.remainingEnabledServerGroups === undefined) {
      stage.remainingEnabledServerGroups = 1;
    }

    ctrl.pluralize = function(str, val) {
      if (val === 1) {
        return str;
      }
      return str + 's';
    };

    if (stage.preferLargerOverNewer === undefined) {
      stage.preferLargerOverNewer = 'false';
    }
    stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
  });

