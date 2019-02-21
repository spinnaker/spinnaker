'use strict';

const angular = require('angular');

import { AccountService, AuthenticationService, BakeryReader, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.pipeline.stage.bakeStage', [require('./bakeExecutionDetails.controller').name])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'oracle',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelTemplateUrl: require('core/pipeline/config/stages/bake/BakeExecutionLabel'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'accountName' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'baseOs' },
        { type: 'requiredField', fieldName: 'upgrade' },
        { type: 'requiredField', fieldName: 'cloudProviderType' },
        { type: 'requiredField', fieldName: 'amiName', fieldLabel: 'Image Name' },
      ],
      restartable: true,
    });
  })
  .controller('oracleBakeStageCtrl', ['$scope', '$q', function($scope, $q) {
    const provider = 'oracle';

    if (!$scope.stage.cloudProvider) {
      $scope.stage.cloudProvider = provider;
    }

    if (!$scope.stage) {
      $scope.stage = {};
    }

    if (!$scope.stage.extended_attributes) {
      // eslint-disable-next-line @typescript-eslint/camelcase
      $scope.stage.extended_attributes = {};
    }

    if (!$scope.stage.user) {
      $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
    }

    function initialize() {
      $scope.viewState.providerSelected = true;

      $q.all({
        baseOsOptions: BakeryReader.getBaseOsOptions(provider),
        accounts: AccountService.listAccounts(provider),
      }).then(results => {
        if (results.baseOsOptions.baseImages.length > 0) {
          $scope.baseOsOptions = results.baseOsOptions;
        }
        if (!$scope.stage.user) {
          $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
        }
        if (!$scope.stage.baseOs) {
          $scope.stage.baseOs = $scope.baseOsOptions.baseImages[0].id;
        }
        if (!$scope.stage.upgrade) {
          $scope.stage.upgrade = true;
        }

        $scope.accounts = results.accounts;

        if ($scope.stage.accountName) {
          AccountService.getRegionsForAccount($scope.stage.accountName).then(function(regions) {
            if (Array.isArray(regions) && regions.length != 0) {
              // there is exactly one region per account
              $scope.stage.region = regions[0].name;
            }
          });
        }

        $scope.viewState.loading = false;
      });
    }

    this.getBaseOsDescription = function(baseOsOption) {
      return baseOsOption.id + (baseOsOption.shortDescription ? ' (' + baseOsOption.shortDescription + ')' : '');
    };

    this.accountUpdated = function() {
      AccountService.getRegionsForAccount($scope.stage.accountName).then(function(regions) {
        if (Array.isArray(regions) && regions.length != 0) {
          // there is exactly one region per account
          $scope.stage.region = regions[0].name;
        }
      });
    };

    $scope.$watch('stage.accountName', $scope.accountUpdated);

    initialize();
  }]);
