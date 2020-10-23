'use strict';

import { module } from 'angular';
import _ from 'lodash';

import {
  ArtifactReferenceService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExpectedArtifactService,
  PipelineTemplates,
  Registry,
  SETTINGS,
} from '@spinnaker/core';
import { GOOGLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER } from './bakeExecutionDetails.controller';

export const GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE = 'spinnaker.gce.pipeline.stage..bakeStage';
export const name = GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE, [GOOGLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'gce',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      extraLabelLines: (stage) => {
        return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
      },
      producesArtifacts: true,
      supportsCustomTimeout: true,
      validators: [
        {
          type: 'anyFieldRequired',
          fields: [
            { fieldName: 'package', fieldLabel: 'Package' },
            { fieldName: 'packageArtifactIds', fieldLabel: 'Package Artifacts' },
          ],
        },
      ],
      restartable: true,
      artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['packageArtifactIds']),
      artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['packageArtifactIds']),
    });
  })
  .controller('gceBakeStageCtrl', [
    '$scope',
    '$q',
    '$uibModal',
    function ($scope, $q, $uibModal) {
      $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};
      $scope.stage.region = 'global';

      if (!$scope.stage.cloudProvider) {
        $scope.stage.cloudProvider = 'gce';
      }

      if (!$scope.stage.user) {
        $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: true,
      };

      function initialize() {
        $scope.viewState.providerSelected = true;
        $q.all([
          BakeryReader.getBaseOsOptions('gce'),
          BakeryReader.getBaseLabelOptions(),
          ExpectedArtifactService.getExpectedArtifactsAvailableToStage($scope.stage, $scope.pipeline),
        ]).then(function ([baseOsOptions, baseLabelOptions, expectedArtifacts]) {
          $scope.baseOsOptions = baseOsOptions.baseImages;
          $scope.baseLabelOptions = baseLabelOptions;
          $scope.viewState.expectedArtifacts = expectedArtifacts;

          if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
            $scope.stage.baseOs = $scope.baseOsOptions[0].id;
          }
          if (!$scope.stage.baseLabel && $scope.baseLabelOptions && $scope.baseLabelOptions.length) {
            $scope.stage.baseLabel = $scope.baseLabelOptions[0];
          }
          $scope.viewState.roscoMode =
            SETTINGS.feature.roscoMode ||
            (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector($scope.stage));
          $scope.showAdvancedOptions = showAdvanced();
          $scope.viewState.loading = false;
        });
      }

      function showAdvanced() {
        const stage = $scope.stage;
        return !!(
          stage.templateFileName ||
          (stage.extendedAttributes && _.size(stage.extendedAttributes) > 0) ||
          stage.varFileName ||
          stage.baseAmi ||
          stage.accountName
        );
      }

      function stageUpdated() {
        deleteEmptyProperties();
        // Since the selector computes using stage as an input, it needs to be able to recompute roscoMode on updates
        if (typeof SETTINGS.feature.roscoSelector === 'function') {
          $scope.viewState.roscoMode = SETTINGS.feature.roscoSelector($scope.stage);
        }
      }

      function deleteEmptyProperties() {
        _.forOwn($scope.stage, function (val, key) {
          if (val === '') {
            delete $scope.stage[key];
          }
        });
      }

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

      this.showTemplateFileName = function () {
        return $scope.viewState.roscoMode || $scope.stage.templateFileName;
      };

      this.showAccountName = function () {
        return $scope.viewState.roscoMode || $scope.stage.accountName;
      };

      this.showExtendedAttributes = function () {
        return (
          $scope.viewState.roscoMode || ($scope.stage.extendedAttributes && _.size($scope.stage.extendedAttributes) > 0)
        );
      };

      this.showVarFileName = function () {
        return $scope.viewState.roscoMode || $scope.stage.varFileName;
      };

      $scope.$watch('stage', stageUpdated, true);

      initialize();
    },
  ]);
