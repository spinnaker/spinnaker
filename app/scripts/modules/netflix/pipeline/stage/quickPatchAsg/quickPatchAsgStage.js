'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsgStage', [
  require('../../../../core/pipeline/config/pipelineConfigProvider.js'),
  require('../../../../core/application/listExtractor/listExtractor.service'),
  require('../../../../core/config/settings.js'),
  require('../../../../core/utils/lodash'),
  require('../../../../core/widgets')
])
  .config(function(pipelineConfigProvider, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      pipelineConfigProvider.registerStage({
        label: 'Quick Patch Server Group',
        description: 'Quick Patches a server group',
        key: 'quickPatch',
        controller: 'QuickPatchAsgStageCtrl',
        controllerAs: 'QuickPatchAsgStageCtrl',
        templateUrl: require('./quickPatchAsgStage.html'),
        executionDetailsUrl: require('./quickPatchAsgExecutionDetails.html'),
        validators: [
          {type: 'requiredField', fieldName: 'clusterName', fieldLabel: 'cluster'},
          {type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
          {type: 'requiredField', fieldName: 'region'},
          {type: 'requiredField', fieldName: 'package'},
          {type: 'requiredField', fieldName: 'baseOs'},
        ],
      });
    }
  }).controller('QuickPatchAsgStageCtrl', function($scope, stage, bakeryService, accountService, appListExtractorService, _) {
    $scope.stage = stage;
    $scope.baseOsOptions = ['ubuntu', 'centos'];
    $scope.stage.application = $scope.application.name;
    $scope.stage.healthProviders = ['Discovery'];

    let clusterFilter = (cluster) => {
      let acctFilter = $scope.stage.account ? cluster.account === $scope.stage.account : true;
      let regionFilter = $scope.stage.region ? _.any(cluster.serverGroups, (sg) => sg.region === $scope.stage.region) : true;
      return acctFilter && regionFilter;
    };

    let setClusterList = () => {
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

    $scope.resetSelectedCluster = () => {
      $scope.stage.clusterName = undefined;
      setClusterList();
    };

    $scope.accountUpdated = function() {
      let accountFilter = (cluster) => cluster.account === $scope.stage.credentials;

      $scope.regions = appListExtractorService.getRegions([$scope.application], accountFilter)
        .map((region) => ({name: region}));

      $scope.regionsLoaded = true;
      $scope.stage.account = $scope.stage.credentials;
      $scope.resetSelectedCluster();
    };

    (function() {
      if ($scope.stage.credentials) {
        $scope.accountUpdated();
      }
    })();

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

