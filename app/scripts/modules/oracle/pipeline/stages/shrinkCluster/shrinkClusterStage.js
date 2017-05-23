'use strict';

const angular = require('angular');

import {ACCOUNT_SERVICE, PipelineTemplates} from '@spinnaker/core';

module.exports = angular.module('spinnaker.core.pipeline.stage.oraclebmcs.shrinkClusterStage', [
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'oraclebmcs',
      templateUrl: require('./shrinkClusterStage.html'),
      executionDetailsUrl: PipelineTemplates.shrinkClusterExecutionDetails,
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('oraclebmcsShrinkClusterStageCtrl', function($scope, accountService) {

    let ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('oraclebmcs').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    stage.regions = stage.regions || [];

    stage.cloudProvider = 'oraclebmcs';

    if (!stage.credentials && $scope.application.defaultCredentials.oraclebmcs) {
      stage.credentials = $scope.application.defaultCredentials.oraclebmcs;
    }

    if (!stage.regions.length && $scope.application.defaultRegions.oraclebmcs) {
      stage.regions.push($scope.application.defaultRegions.oraclebmcs);
    }

    if (stage.shrinkToSize === undefined) {
      stage.shrinkToSize = 1;
    }

    if (stage.allowDeleteActive === undefined) {
      stage.allowDeleteActive = false;
    }

    ctrl.pluralize = function(str, val) {
      return (val === 1) ? str : str + 's';
    };

    if (stage.retainLargerOverNewer === undefined) {
      stage.retainLargerOverNewer = 'false';
    }

    stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
  });
