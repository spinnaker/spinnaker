'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.shrinkClusterStage', [
  require('../../../../../../core/application/listExtractor/listExtractor.service'),
  require('../../../../../account/account.service.js'),
  require('./shrinkClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'gce',
      templateUrl: require('./shrinkClusterStage.html'),
      executionDetailsUrl: require('./shrinkClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups'},
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('gceShrinkClusterStageCtrl', function($scope, accountService, appListExtractorService) {
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

    ctrl.resetSelectedCluster = () => {
      $scope.stage.cluster = undefined;
      setClusterList();
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
      setClusterList();
    });

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

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'gce';

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }

    if (stage.shrinkToSize === undefined) {
      stage.shrinkToSize = 1;
    }

    if (stage.allowDeleteActive === undefined) {
      stage.allowDeleteActive = false;
    }

    ctrl.pluralize = function(str, val) {
      if (val === 1) {
        return str;
      }
      return str + 's';
    };

    if (stage.retainLargerOverNewer === undefined) {
      stage.retainLargerOverNewer = 'false';
    }
    stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

