'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  AuthenticationService,
  BakeryReader,
  BakeExecutionLabel,
  Registry,
  PipelineTemplates,
  SETTINGS,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.pipeline.stage.bakeStage', [require('./bakeExecutionDetails.controller').name])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'openstack',
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
  .controller('openstackBakeStageCtrl', [
    '$scope',
    '$q',
    '$uibModal',
    function($scope, $q, $uibModal) {
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
          regions: BakeryReader.getRegions('openstack'),
          baseOsOptions: BakeryReader.getBaseOsOptions('openstack'),
          baseLabelOptions: BakeryReader.getBaseLabelOptions(),
        }).then(function(results) {
          $scope.regions = results.regions;
          if ($scope.regions.length === 1) {
            $scope.stage.region = $scope.regions[0];
          } else if (!$scope.regions.includes($scope.stage.region)) {
            delete $scope.stage.region;
          }
          if (!$scope.stage.regions.length && $scope.application.defaultRegions.openstack) {
            $scope.stage.regions.push($scope.application.defaultRegions.openstack);
          }
          if (!$scope.stage.regions.length && $scope.application.defaultRegions.openstack) {
            $scope.stage.regions.push($scope.application.defaultRegions.openstack);
          }
          $scope.baseOsOptions = results.baseOsOptions.baseImages;
          $scope.baseLabelOptions = results.baseLabelOptions;

          if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
            $scope.stage.baseOs = $scope.baseOsOptions[0].id;
          }
          $scope.viewState.roscoMode = SETTINGS.feature.roscoMode;
          $scope.showAdvancedOptions = showAdvanced();
          $scope.viewState.loading = false;
        });
      }

      function showAdvanced() {
        let stage = $scope.stage;
        return !!(stage.templateFileName || (stage.extendedAttributes && _.size(stage.extendedAttributes) > 0));
      }

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

      this.showExtendedAttributes = function() {
        return (
          $scope.viewState.roscoMode || ($scope.stage.extendedAttributes && _.size($scope.stage.extendedAttributes) > 0)
        );
      };

      this.showTemplateFileName = function() {
        return $scope.viewState.roscoMode || $scope.stage.templateFileName;
      };

      $scope.$watch('stage', deleteEmptyProperties, true);

      initialize();
    },
  ]);
