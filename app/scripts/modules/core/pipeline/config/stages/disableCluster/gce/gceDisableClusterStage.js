'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.disableClusterStage', [
  require('../../../../../utils/lodash.js'),
  require('../../stageConstants.js'),
  require('./disableClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'gce',
      templateUrl: require('./disableClusterStage.html'),
      executionDetailsUrl: require('./disableClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups'},
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('gceDisableClusterStageCtrl', function($scope, accountService, stageConstants, _) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      zonesLoaded: false
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.zones = {"us-central1": ['us-central1-a', 'us-central1-b', 'us-central1-c']};

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(zoneMap) {
        $scope.zones = zoneMap;
        $scope.zonesLoaded = true;
      });
    };

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }
    if (!stage.zones.length && $scope.application.defaultRegions.gce) {
      stage.zones.push($scope.application.defaultRegions.gce);
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
      stage.preferLargerOverNewer = "false";
    }
    stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
  });

