'use strict';

const angular = require('angular');

import {
  ACCOUNT_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.oraclebmcs.pipeline.stage.scaleDownClusterStage', [
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'oraclebmcs',
      templateUrl: require('./scaleDownClusterStage.html'),
      executionDetailsUrl: require('./scaleDownClusterExecutionDetails.html'),
      executionConfigSections: ['scaleDownClusterConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingFullSizeServerGroups', fieldLabel: 'Keep [X] full size Server Groups'},
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
      strategy: true,
    });
  }).controller('oraclebmcsScaleDownClusterStageCtrl', function($scope, accountService) {

    let stage = $scope.stage;

    const provider = 'oraclebmcs';

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts(provider).then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    stage.regions = stage.regions || [];
    stage.cloudProvider = provider;

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }

    if (!stage.regions.length && $scope.application.defaultRegions.gce) {
      stage.regions.push($scope.application.defaultRegions.gce);
    }

    if (stage.remainingFullSizeServerGroups === undefined) {
      stage.remainingFullSizeServerGroups = 1;
    }

    if (stage.allowScaleDownActive === undefined) {
      stage.allowScaleDownActive = false;
    }

    if (stage.preferLargerOverNewer === undefined) {
      stage.preferLargerOverNewer = 'false';
    }

    this.pluralize = function(str, val) {
      return val === 1 ? str : str + 's';
    };

    stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
  });
