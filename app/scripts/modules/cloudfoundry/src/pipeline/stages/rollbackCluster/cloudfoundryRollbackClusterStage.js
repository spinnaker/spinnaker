'use strict';

const angular = require('angular');

import { AccountService, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cloudfoundry.pipeline.stage.rollbackClusterStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'rollbackCluster',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./rollbackClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('cloudfoundryRollbackClusterStageCtrl', function($scope) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    AccountService.listAccounts('cloudfoundry').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.reset = () => {
      ctrl.accountUpdated();
      ctrl.resetSelectedCluster();
    };

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'cloudfoundry';
    stage.targetHealthyRollbackPercentage = 100;

    if (!stage.credentials && $scope.application.defaultCredentials.cloudfoundry) {
      stage.credentials = $scope.application.defaultCredentials.cloudfoundry;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.cloudfoundry) {
      stage.regions.push($scope.application.defaultRegions.cloudfoundry);
    }
  });
