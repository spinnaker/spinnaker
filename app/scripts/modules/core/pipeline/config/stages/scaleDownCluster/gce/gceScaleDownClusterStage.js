'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.scaleDownClusterStage', [
  require('../../../../../utils/lodash.js'),
  require('../../stageConstants.js'),
  require('./scaleDownClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'gce',
      templateUrl: require('./scaleDownClusterStage.html'),
      executionDetailsUrl: require('./scaleDownClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingFullSizeServerGroups', fieldLabel: 'Keep [X] full size Server Groups'},
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
      strategy: true,
    });
  }).controller('gceScaleDownClusterStageCtrl', function($scope, accountService, stageConstants, _) {
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

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }
    if (!stage.zones.length && $scope.application.defaultRegions.gce) {
      stage.zones.push($scope.application.defaultRegions.gce);
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }

    if (stage.remainingFullSizeServerGroups === undefined) {
      stage.remainingFullSizeServerGroups = 1;
    }

    if (stage.allowScaleDownActive === undefined) {
      stage.allowScaleDownActive = false;
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

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

