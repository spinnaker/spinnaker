'use strict';

const angular = require('angular');

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cloudfoundry.pipeline.stage.enableAsgStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./enableAsgStage.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('cloudfoundryEnableAsgStageCtrl', function($scope) {
    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    AccountService.listAccounts('cloudfoundry').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.targets = StageConstants.TARGET_LIST;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'cloudfoundry';

    if (
      stage.isNew &&
      $scope.application.attributes.platformHealthOnlyShowOverride &&
      $scope.application.attributes.platformHealthOnly
    ) {
      stage.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cloudfoundry) {
      stage.credentials = $scope.application.defaultCredentials.cloudfoundry;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.cloudfoundry) {
      stage.regions.push($scope.application.defaultRegions.cloudfoundry);
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });
