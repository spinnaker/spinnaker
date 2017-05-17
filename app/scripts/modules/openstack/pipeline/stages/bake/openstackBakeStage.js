'use strict';

const angular = require('angular');
import _ from 'lodash';

import { BakeExecutionLabel, BAKERY_SERVICE, PIPELINE_CONFIG_PROVIDER, SETTINGS } from '@spinnaker/core';

module.exports = angular.module('spinnaker.core.pipeline.stage.openstack.bakeStage', [
  PIPELINE_CONFIG_PROVIDER,
  require('./bakeExecutionDetails.controller.js'),
  BAKERY_SERVICE,
  require('core/pipeline/config/stages/bake/modal/addExtendedAttribute.controller.modal.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'bake',
      cloudProvider: 'openstack',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      extraLabelLines: (stage) => {
        return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
      },
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'package', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'stageOrTriggerBeforeType',
          stageTypes: ['jenkins', 'travis'],
          checkParentTriggers: true,
          message: 'Bake stages should always have a Jenkins/Travis stage or trigger preceding them.<br> Otherwise, ' +
        'Spinnaker will bake and deploy the most-recently built package.'}
      ],
      restartable: true,
    });
  })
  .controller('openstackBakeStageCtrl', function($scope, bakeryService, $q, authenticationService) {

    $scope.stage.extendedAttributes = $scope.stage.extendedAttributes || {};
    $scope.stage.regions = $scope.stage.regions || [];

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true,
    };

    function initialize() {
      $q.all({
        regions: bakeryService.getRegions('openstack'),
        baseOsOptions: bakeryService.getBaseOsOptions('openstack'),
        baseLabelOptions: bakeryService.getBaseLabelOptions()
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
        $scope.viewState.loading = false;
      });
    }

    function deleteEmptyProperties() {
      _.forOwn($scope.stage, function(val, key) {
        if (val === '') {
          delete $scope.stage[key];
        }
      });
    }

    this.showTemplateFileName = function() {
      return $scope.viewState.roscoMode || $scope.stage.templateFileName;
    };

    $scope.$watch('stage', deleteEmptyProperties, true);

    initialize();
  });
