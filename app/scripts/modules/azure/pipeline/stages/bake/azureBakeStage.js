'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  Registry,
  PipelineTemplates,
  SETTINGS,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.pipeline.stage.bakeStage', [require('./bakeExecutionDetails.controller').name])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'azure',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      extraLabelLines: stage => {
        return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
      },
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'package' },
        { type: 'requiredField', fieldName: 'regions' },
        {
          type: 'upstreamVersionProvided',
          checkParentTriggers: true,
          getMessage: labels =>
            'Bake stages should always have a stage or trigger preceding them that provides version information: ' +
            '<ul>' +
            labels.map(label => `<li>${label}</li>`).join('') +
            '</ul>' +
            'Otherwise, Spinnaker will bake and deploy the most-recently built package.',
        },
      ],
      restartable: true,
    });
  })
  .controller('azureBakeStageCtrl', ['$scope', '$q', '$uibModal', function($scope, $q, $uibModal) {
    $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};
    $scope.stage.regions = $scope.stage.regions || [];

    if (!$scope.stage.user) {
      $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true,
    };

    function initialize() {
      $q.all({
        regions: BakeryReader.getRegions('azure'),
        baseOsOptions: BakeryReader.getBaseOsOptions('azure'),
        baseLabelOptions: BakeryReader.getBaseLabelOptions(),
      }).then(function(results) {
        $scope.regions = results.regions;
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
        $scope.baseOsOptions = results.baseOsOptions.baseImages;
        if ($scope.baseOsOptions.length) {
          $scope.stage.osType = results.baseOsOptions.baseImages[0].osType;
        }

        $scope.baseLabelOptions = results.baseLabelOptions;

        if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
          $scope.stage.baseOs = $scope.baseOsOptions[0].id;
        }

        if (!$scope.stage.baseLabel && $scope.baseLabelOptions && $scope.baseLabelOptions.length) {
          $scope.stage.baseLabel = $scope.baseLabelOptions[0];
        }
        $scope.viewState.roscoMode = SETTINGS.feature.roscoMode;
        $scope.viewState.loading = false;
      });
    }

    this.baseOsChanged = () => {
      var selectedOption = _.find($scope.baseOsOptions, { id: $scope.stage.baseOs });
      $scope.stage.osType = selectedOption.osType;
    };

    function deleteEmptyProperties() {
      _.forOwn($scope.stage, function(val, key) {
        if (val === '') {
          delete $scope.stage[key];
        }
      });
    }

    this.addExtendedAttribute = function() {
      if (!$scope.stage.extendedAttributes) {
        $scope.stage.extendedAttributes = {};
      }
      $uibModal
        .open({
          templateUrl: PipelineTemplates.addExtendedAttributes,
          controller: 'bakeStageAddExtendedAttributeController',
          controllerAs: 'addExtendedAttribute',
          resolve: {
            extendedAttribute: function() {
              return {
                key: '',
                value: '',
              };
            },
          },
        })
        .result.then(function(extendedAttribute) {
          $scope.stage.extendedAttributes[extendedAttribute.key] = extendedAttribute.value;
        })
        .catch(() => {});
    };

    this.removeExtendedAttribute = function(key) {
      delete $scope.stage.extendedAttributes[key];
    };

    this.showTemplateFileName = function() {
      return $scope.viewState.roscoMode || $scope.stage.templateFileName;
    };

    this.showExtendedAttributes = function() {
      return (
        $scope.viewState.roscoMode || ($scope.stage.extendedAttributes && _.size($scope.stage.extendedAttributes) > 0)
      );
    };

    this.showVarFileName = function() {
      return $scope.viewState.roscoMode || $scope.stage.varFileName;
    };

    $scope.$watch('stage', deleteEmptyProperties, true);

    initialize();
  }]);
