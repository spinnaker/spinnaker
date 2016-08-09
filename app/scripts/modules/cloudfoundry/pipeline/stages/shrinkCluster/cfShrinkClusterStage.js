'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.shrinkClusterStage', [
  require('../../../../core/account/account.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'cf',
      templateUrl: require('./shrinkClusterStage.html'),
      executionDetailsUrl: require('./shrinkClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('cfShrinkClusterStageCtrl', function($scope, accountService) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
    };

    accountService.listAccounts('cf').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      accountService.getAccountDetails(stage.credentials).then(function(details) {
        stage.regions = [details.org];
      });
    };

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'cf';

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
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

