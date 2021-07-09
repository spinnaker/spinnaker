'use strict';

import { module } from 'angular';

import {
  AccountService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  PipelineTemplates,
  Registry,
} from '@spinnaker/core';

import { ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER } from './bakeExecutionDetails.controller';

export const ORACLE_PIPELINE_STAGES_BAKE_OCIBAKESTAGE = 'spinnaker.oracle.pipeline.stage.bakeStage';
export const name = ORACLE_PIPELINE_STAGES_BAKE_OCIBAKESTAGE; // for backwards compatibility
module(ORACLE_PIPELINE_STAGES_BAKE_OCIBAKESTAGE, [ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'oracle',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      supportsCustomTimeout: true,
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
  .controller('oracleBakeStageCtrl', [
    '$scope',
    '$q',
    '$uibModal',
    function ($scope, $q, $uibModal) {
      const provider = 'oracle';

      if (!$scope.stage.cloudProvider) {
        $scope.stage.cloudProvider = provider;
      }

      if (!$scope.stage) {
        $scope.stage = {};
      }

      $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};

      if (!$scope.stage.user) {
        $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
      }

      function initialize() {
        $scope.viewState.providerSelected = true;

        $q.all([BakeryReader.getBaseOsOptions(provider), AccountService.listAccounts(provider)]).then(
          ([baseOsOptions, accounts]) => {
            if (baseOsOptions.baseImages.length > 0) {
              $scope.baseOsOptions = baseOsOptions;
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

            $scope.accounts = accounts;

            if ($scope.stage.accountName) {
              AccountService.getRegionsForAccount($scope.stage.accountName).then(function (regions) {
                if (Array.isArray(regions) && regions.length != 0) {
                  // there is exactly one region per account
                  $scope.stage.region = regions[0].name;
                }
              });
            }

            $scope.viewState.loading = false;
          },
        );
      }

      this.getBaseOsDescription = function (baseOsOption) {
        return baseOsOption.id + (baseOsOption.shortDescription ? ' (' + baseOsOption.shortDescription + ')' : '');
      };

      this.accountUpdated = function () {
        AccountService.getRegionsForAccount($scope.stage.accountName).then(function (regions) {
          if (Array.isArray(regions) && regions.length != 0) {
            // there is exactly one region per account
            $scope.stage.region = regions[0].name;
          }
        });
      };

      this.addExtendedAttribute = function () {
        if (!$scope.stage.extendedAttributes) {
          $scope.stage.extendedAttributes = {};
        }
        $uibModal
          .open({
            templateUrl: PipelineTemplates.addExtendedAttributes,
            controller: 'bakeStageAddExtendedAttributeController',
            controllerAs: 'addExtendedAttribute',
            resolve: {
              extendedAttribute: function () {
                return {
                  key: '',
                  value: '',
                };
              },
            },
          })
          .result.then(function (extendedAttribute) {
            $scope.stage.extendedAttributes[extendedAttribute.key] = extendedAttribute.value;
          })
          .catch(() => {});
      };

      this.removeExtendedAttribute = function (key) {
        delete $scope.stage.extendedAttributes[key];
      };

      this.showExtendedAttributes = function () {
        return (
          $scope.viewState.roscoMode || ($scope.stage.extendedAttributes && _.size($scope.stage.extendedAttributes) > 0)
        );
      };

      $scope.$watch('stage.accountName', $scope.accountUpdated);

      initialize();
    },
  ]);
