'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

//BEN_TODO: where is this defined?

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.disableClusterStage', [
  ACCOUNT_SERVICE,
  require('./disableClusterExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'kubernetes',
      templateUrl: require('./disableClusterStage.html'),
      executionDetailsUrl: require('./disableClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups'},
        { type: 'requiredField', fieldName: 'namespaces', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('kubernetesDisableClusterStageCtrl', function($scope, accountService) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      namespacesLoaded: false
    };


    accountService.listAccounts('kubernetes').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });


    stage.namespaces = stage.namespaces || [];
    stage.cloudProvider = 'kubernetes';
    stage.interestingHealthProviderNames = ['KubernetesService'];

    if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      stage.credentials = $scope.application.defaultCredentials.kubernetes;
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
      stage.preferLargerOverNewer = 'false';
    }
    stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
  });

