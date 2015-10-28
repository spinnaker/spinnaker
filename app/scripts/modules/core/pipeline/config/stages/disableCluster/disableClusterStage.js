'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?

module.exports = angular.module('spinnaker.core.pipeline.stage.disableClusterStage', [
  require('../../../../utils/lodash.js'),
  require('../stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Disable Cluster',
      description: 'Disables a cluster',
      key: 'disableCluster',
      alias: 'disableCluster',
      controller: 'DisableClusterStageCtrl',
      controllerAs: 'disableClusterStageCtrl',
      templateUrl: require('./disableClusterStage.html'),
      executionDetailsUrl: require('./disableClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('DisableClusterStageCtrl', function($scope, stage, accountService, stageConstants, _) {
    var ctrl = this;

    $scope.stage = stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.state.regionsLoaded = true;
      });
    };

    stage.regions = stage.regions || [];

    if (!stage.credentials && $scope.application.defaultCredentials) {
      stage.credentials = $scope.application.defaultCredentials;
    }
    if (!stage.regions.length && $scope.application.defaultRegion) {
      stage.regions.push($scope.application.defaultRegion);
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
  })
  .name;

