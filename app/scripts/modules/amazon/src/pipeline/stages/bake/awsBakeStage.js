'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AuthenticationService } from '@spinnaker/core';
import { BakeExecutionLabel, BakeryReader, PipelineTemplates, Registry, SETTINGS } from '@spinnaker/core';
import { AWSProviderSettings } from '../../../aws.settings';

import { AMAZON_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER } from './bakeExecutionDetails.controller';

export const AMAZON_PIPELINE_STAGES_BAKE_AWSBAKESTAGE = 'spinnaker.amazon.pipeline.stage.bakeStage';
export const name = AMAZON_PIPELINE_STAGES_BAKE_AWSBAKESTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_BAKE_AWSBAKESTAGE, [AMAZON_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'aws',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      extraLabelLines: (stage) => {
        return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
      },
      supportsCustomTimeout: true,
      validators: [
        { type: 'requiredField', fieldName: 'package' },
        { type: 'requiredField', fieldName: 'regions' },
        {
          type: 'upstreamVersionProvided',
          checkParentTriggers: true,
          getMessage: (labels) =>
            'Bake stages should always have a stage or trigger preceding them that provides version information: ' +
            '<ul>' +
            labels.map((label) => `<li>${label}</li>`).join('') +
            '</ul>' +
            'Otherwise, Spinnaker will bake and deploy the most-recently built package.',
        },
      ],
      restartable: true,
    });
  })
  .controller('awsBakeStageCtrl', [
    '$scope',
    '$q',
    '$uibModal',
    function ($scope, $q, $uibModal) {
      $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};
      $scope.stage.regions = ($scope.stage.regions && $scope.stage.regions.sort()) || [];

      if (!$scope.stage.user) {
        $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: true,
        roscoMode:
          SETTINGS.feature.roscoMode ||
          (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector($scope.stage)),
        minRootVolumeSize: AWSProviderSettings.minRootVolumeSize,
        showVmTypeSelector: true,
        bakeWarning: AWSProviderSettings.bakeWarning,
        showMigrationFields: $scope.pipeline.migrationStatus !== 'Started',
      };

      function initialize() {
        $q.all([
          BakeryReader.getRegions('aws'),
          BakeryReader.getBaseOsOptions('aws'),
          BakeryReader.getBaseLabelOptions(),
          ['ebs', 'docker'],
        ]).then(function ([regions, baseOsOptions, baseLabelOptions, storeTypes]) {
          $scope.regions = [...regions].sort();
          $scope.storeTypes = storeTypes;
          if (!$scope.stage.storeType && $scope.storeTypes && $scope.storeTypes.length) {
            $scope.stage.storeType = $scope.storeTypes[0];
          }
          if ($scope.regions.length === 1) {
            $scope.stage.region = $scope.regions[0];
          } else if (!$scope.regions.includes($scope.stage.region)) {
            delete $scope.stage.region;
          }
          if (!$scope.stage.regions.length && $scope.application.defaultRegions.aws) {
            $scope.stage.regions.push(...Object.keys($scope.application.defaultRegions.aws).sort());
          }
          $scope.baseOsOptions = baseOsOptions.baseImages;
          $scope.baseLabelOptions = baseLabelOptions;

          if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
            $scope.stage.baseOs = $scope.baseOsOptions[0].id;
          } else if (
            $scope.stage.baseOs &&
            !($scope.baseOsOptions || []).find((baseOs) => baseOs.id === $scope.stage.baseOs)
          ) {
            $scope.baseOsOptions.push({
              id: $scope.stage.baseOs,
              detailedDescription: 'Custom',
              vmTypes: ['hvm', 'pv'],
            });
          }
          if (!$scope.stage.baseLabel && $scope.baseLabelOptions && $scope.baseLabelOptions.length) {
            $scope.stage.baseLabel = $scope.baseLabelOptions[0];
          }
          setVmTypes();
          if (!$scope.stage.vmType && $scope.vmTypes && $scope.vmTypes.length) {
            $scope.stage.vmType = $scope.vmTypes[0];
          }
          $scope.showAdvancedOptions = showAdvanced();
          $scope.viewState.loading = false;
        });
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

      function showAdvanced() {
        const stg = $scope.stage;
        return !!(
          stg.templateFileName ||
          (stg.extendedAttributes && _.size(stg.extendedAttributes) > 0) ||
          stg.varFileName ||
          stg.baseName ||
          stg.baseAmi ||
          stg.amiName ||
          stg.amiSuffix ||
          stg.rootVolumeSize
        );
      }

      function setVmTypes() {
        if ($scope.baseOsOptions.length && $scope.baseOsOptions.every(({ vmTypes }) => vmTypes)) {
          const allVmTypes =
            $scope.baseOsOptions.length &&
            new Set($scope.baseOsOptions.reduce((types, { vmTypes }) => types.concat(vmTypes), []));
          const baseOs = $scope.baseOsOptions.find(({ id }) => id === $scope.stage.baseOs);

          $scope.viewState.showVmTypeSelector = allVmTypes.size > 1;
          $scope.vmTypes = baseOs.vmTypes;
        } else {
          $scope.viewState.showVmTypeSelector = true;
          $scope.vmTypes = ['hvm', 'pv'];
        }
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

      this.showExtendedAttributes = function () {
        return (
          $scope.viewState.roscoMode || ($scope.stage.extendedAttributes && _.size($scope.stage.extendedAttributes) > 0)
        );
      };

      this.showVarFileName = function () {
        return $scope.viewState.roscoMode || $scope.stage.varFileName;
      };

      this.handleBaseOsChange = function () {
        setVmTypes();
        if ($scope.vmTypes && $scope.vmTypes.length && !$scope.vmTypes.includes($scope.stage.vmType)) {
          $scope.stage.vmType = $scope.vmTypes[0];
        }
      };

      $scope.$watch('stage', stageUpdated, true);

      initialize();
    },
  ]);
