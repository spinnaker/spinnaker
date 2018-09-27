'use strict';

const angular = require('angular');

import { AccountService, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cloudfoundry.pipeline.stage.deleteServiceStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'deleteService',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./deleteServiceStage.html'),
      executionStepLabelUrl: require('./deleteServiceStepLabel.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        { type: 'requiredField', fieldName: 'action' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'serviceName', preventSave: true },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('CloudfoundryDeleteServiceStageCtrl', function($scope) {
    let stage = $scope.stage;
    stage.action = 'deleteService';
    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };
    AccountService.listAccounts('cloudfoundry').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    AccountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
      $scope.regions = regions;
    });

    stage.cloudProvider = 'cloudfoundry';

    $scope.onAccountChange = () => {
      $scope.stage.service = null;
      $scope.serviceNamesAndPlans = [];

      AccountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = regions;
      });
    };
  });
