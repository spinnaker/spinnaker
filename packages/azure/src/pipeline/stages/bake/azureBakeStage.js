'use strict';

import { module } from 'angular';
import _ from 'lodash';

import {
  AccountService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  PipelineTemplates,
  Registry,
  SETTINGS,
} from '@spinnaker/core';

import { AZURE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER } from './bakeExecutionDetails.controller';
import { AZURE_IMAGE_IMAGE_READER } from '../../../image/image.reader';
import { AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from './../../../serverGroup/configure/serverGroupCommandBuilder.service';

export const AZURE_PIPELINE_STAGES_BAKE_AZUREBAKESTAGE = 'spinnaker.azure.pipeline.stage.bakeStage';
export const name = AZURE_PIPELINE_STAGES_BAKE_AZUREBAKESTAGE; // for backwards compatibility
module(AZURE_PIPELINE_STAGES_BAKE_AZUREBAKESTAGE, [
  AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
  AZURE_IMAGE_IMAGE_READER,
  AZURE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER,
])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'azure',
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
  .controller('azureBakeStageCtrl', [
    '$scope',
    '$q',
    'azureImageReader',
    '$uibModal',
    function ($scope, $q, azureImageReader, $uibModal) {
      $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};
      $scope.stage.regions = $scope.stage.regions || [];

      if (!$scope.stage.user) {
        $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: true,
      };

      function initialize() {
        $q.all([
          AccountService.getCredentialsKeyedByAccount('azure'),
          BakeryReader.getRegions('azure'),
          BakeryReader.getBaseOsOptions('azure'),
          BakeryReader.getBaseLabelOptions(),
        ]).then(function ([credentialsKeyedByAccount, regions, baseOsOptions, baseLabelOptions]) {
          $scope.accounts = Object.keys(credentialsKeyedByAccount);
          $scope.regions = regions;
          if ($scope.regions.length === 1) {
            $scope.stage.region = $scope.regions[0];
          } else if (!$scope.regions.includes($scope.stage.region)) {
            delete $scope.stage.region;
          }
          if (!$scope.stage.regions.length && $scope.application.defaultRegions.azure) {
            $scope.stage.regions.push($scope.application.defaultRegions.azure);
          }
          if (!$scope.stage.regions.length && $scope.application.defaultRegions.azure) {
            $scope.stage.regions.push($scope.application.defaultRegions.azure);
          }
          $scope.baseOsOptions = baseOsOptions.baseImages;

          $scope.baseLabelOptions = baseLabelOptions;
          $scope.osTypeOptions = ['linux', 'windows'];
          $scope.packageTypeOptions = ['DEB', 'RPM'];

          $scope.viewState.roscoMode =
            SETTINGS.feature.roscoMode ||
            (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector($scope.stage));
          $scope.showAdvancedOptions = showAdvanced();
          $scope.viewState.loading = false;

          if ($scope.stage.managedImage != null) {
            $scope.managedImagesWasChosen = true;
            setManagedImages();
          }
          $scope.defaultImagesWasChosen = $scope.stage.baseOs != null;
          $scope.customImagesWasChosen = $scope.stage.publisher != null;
        });
      }

      function stageUpdated() {
        deleteEmptyProperties();
        // Since the selector computes using stage as an input, it needs to be able to recompute roscoMode on updates
        if (typeof SETTINGS.feature.roscoSelector === 'function') {
          $scope.viewState.roscoMode = SETTINGS.feature.roscoSelector($scope.stage);
        }
      }

      function showAdvanced() {
        const stg = $scope.stage;
        return !!(
          stg.templateFileName ||
          (stg.extendedAttributes && _.size(stg.extendedAttributes) > 0) ||
          stg.varFileName
        );
      }

      function deleteEmptyProperties() {
        _.forOwn($scope.stage, function (val, key) {
          if (val === '') {
            delete $scope.stage[key];
          }
        });
      }

      function setManagedImages() {
        azureImageReader
          .findImages({ provider: 'azure', managedImages: true, account: $scope.stage.account })
          .then(function (images) {
            let managedImageOptions = [];
            for (let i in images) {
              let image = images[i];
              let newImage = {
                id: image.imageName,
                osType: image.ostype,
                name: image.imageName,
              };
              managedImageOptions.push(newImage);
            }
            $scope.managedImageOptions = managedImageOptions;
          })
          .catch(() => {});
      }

      function setRegions() {
        AccountService.getRegionsForAccount($scope.stage.account)
          .then(function (regions) {
            $scope.regions = regions.map((r) => r.name);
          })
          .catch(() => {});
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

      this.showDefaultImages = function () {
        $scope.managedImagesWasChosen = false;
        $scope.defaultImagesWasChosen = true;
        $scope.customImagesWasChosen = false;

        $scope.stage.managedImage = null;
        $scope.stage.publisher = null;
        $scope.stage.offer = null;
        $scope.stage.sku = null;
        $scope.stage.osType = null;
        $scope.stage.packageType = null;
      };

      this.showManagedImages = function () {
        setManagedImages();

        $scope.managedImagesWasChosen = true;
        $scope.defaultImagesWasChosen = false;
        $scope.customImagesWasChosen = false;

        $scope.stage.osType = null;
        $scope.stage.baseOs = null;
        $scope.stage.publisher = null;
        $scope.stage.offer = null;
        $scope.stage.sku = null;
        $scope.stage.packageType = null;
      };

      this.showCustomImages = function () {
        $scope.managedImagesWasChosen = false;
        $scope.defaultImagesWasChosen = false;
        $scope.customImagesWasChosen = true;

        $scope.stage.baseOs = null;
        $scope.stage.managedImage = null;
        $scope.stage.osType = null;
        $scope.stage.packageType = null;
      };

      this.onChangeAccount = () => {
        $scope.stage.osType = null;
        $scope.stage.packageType = null;
        $scope.stage.managedImage = null;

        if ($scope.stage.account) {
          setRegions();
        }

        if ($scope.managedImagesWasChosen) {
          setManagedImages();
        }
      };

      $scope.onChangeManagedImage = () => {
        $scope.stage.packageType = null;
        const selectedManagedImage = _.find($scope.managedImageOptions, { id: $scope.stage.managedImage });
        $scope.stage.osType = selectedManagedImage.osType.toLowerCase();
      };

      this.onChangeOsType = function (e) {
        $scope.stage.packageType = null;
      };

      $scope.$watch('stage', stageUpdated, true);

      initialize();
    },
  ]);
