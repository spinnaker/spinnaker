'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.shrinkClusterStage', [
  require('../../../../../utils/lodash.js'),
  require('../../stageConstants.js'),
  require('./shrinkClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'aws',
      templateUrl: require('./shrinkClusterStage.html'),
      executionDetailsUrl: require('./shrinkClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('awsShrinkClusterStageCtrl', function($scope, accountService, stageConstants, _) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('aws').then(function (accounts) {
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
    stage.cloudProvider = 'aws';

    if (!stage.credentials && $scope.application.defaultCredentials.aws) {
      stage.credentials = $scope.application.defaultCredentials.aws;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.aws) {
      stage.regions.push($scope.application.defaultRegions.aws);
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
      stage.retainLargerOverNewer = "false";
    }
    stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
  });

