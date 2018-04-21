'use strict';

const angular = require('angular');

import { AccountService } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cf.pipeline.stage.disableClusterStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'cf',
      templateUrl: require('./disableClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        {
          type: 'requiredField',
          fieldName: 'remainingEnabledServerGroups',
          fieldLabel: 'Keep [X] enabled Server Groups',
        },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('cfDisableClusterStageCtrl', function($scope) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
    };

    AccountService.listAccounts('cf').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      AccountService.getAccountDetails(stage.credentials).then(function(details) {
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
