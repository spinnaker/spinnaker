'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.findAmiStage', [
  require('../../../../core/application/listExtractor/listExtractor.service.js'),
  require('./findAmiExecutionDetails.controller.js'),
  require('../../../../core/account/account.service.js'),
  require('../../../../core/config/settings.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'findImage',
      cloudProvider: 'cf',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection'},
        { type: 'requiredField', fieldName: 'credentials' }
      ]
    });
  }).controller('cfFindAmiStageCtrl', function($scope, accountService, appListExtractorService, settings) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
    };

    let setClusterList = () => {
      let clusterFilter = appListExtractorService.clusterFilterForCredentials($scope.stage.credentials);
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    ctrl.resetSelectedCluster = () => {
      $scope.stage.cluster = undefined;
      setClusterList();
    };

    accountService.listAccounts('cf').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

    ctrl.accountUpdated = function() {
    };

    ctrl.updateRegions = function() {
      let preferredZoneList = settings.providers.cf.preferredZonesByAccount[$scope.stage.credentials];
      stage.regions = Object.keys(preferredZoneList);
    };

    ctrl.reset = () => {
      ctrl.accountUpdated();
      ctrl.resetSelectedCluster();
      ctrl.updateRegions();
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

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'cf';
    stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

    if (angular.isUndefined(stage.onlyEnabled)) {
      stage.onlyEnabled = true;
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.cf) {
      stage.regions.push($scope.application.defaultRegions.cf);
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

