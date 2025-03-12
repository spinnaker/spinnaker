'use strict';

import { module } from 'angular';
import React from 'react';
import ReactDOM from 'react-dom';
import { take } from 'rxjs/operators';

import { StageConfigWrapper } from '../StageConfigWrapper';
import { AccountService } from '../../../../account/AccountService';
import { SETTINGS } from '../../../../config/settings';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_BASEPROVIDERSTAGE_BASEPROVIDERSTAGE =
  'spinnaker.core.pipeline.stage.baseProviderStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_BASEPROVIDERSTAGE_BASEPROVIDERSTAGE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_BASEPROVIDERSTAGE_BASEPROVIDERSTAGE, []).controller('BaseProviderStageCtrl', [
  '$scope',
  'stage',
  '$timeout',
  function ($scope, stage, $timeout) {
    // Docker Bake is wedged in here because it doesn't really fit our existing cloud provider paradigm
    const dockerBakeEnabled = SETTINGS.feature.dockerBake && stage.type === 'bake';

    $scope.stage = stage;

    $scope.viewState = $scope.viewState || {};
    $scope.viewState.loading = true;

    const stageProviders = Registry.pipeline.getProvidersFor(stage.type);

    if (dockerBakeEnabled) {
      stageProviders.push({ cloudProvider: 'docker' });
    }

    AccountService.listProviders$($scope.application)
      .pipe(take(1))
      .subscribe(function (providers) {
        $scope.viewState.loading = false;
        const availableProviders = [];
        stageProviders.forEach((sp) => {
          if (sp.cloudProvider && providers.includes(sp.cloudProvider)) {
            // default to the specified cloud provider if the app supports it
            availableProviders.push(sp.cloudProvider);
          } else if (sp.providesFor) {
            availableProviders.push(...sp.providesFor.filter((p) => providers.includes(p)));
          }
        });
        if (dockerBakeEnabled) {
          availableProviders.push('docker');
        }
        if (availableProviders.length === 1) {
          $scope.stage.cloudProviderType = availableProviders[0];
        } else if (!$scope.stage.cloudProviderType && $scope.stage.cloudProvider) {
          // This addresses the situation where a pipeline includes a stage from before it was made multi-provider.
          $scope.stage.cloudProviderType = $scope.stage.cloudProvider;
        } else {
          $scope.providers = availableProviders;
        }
      });

    let reactMounted = false;
    function loadProvider() {
      const stageProvider = (stageProviders || []).find(
        (s) => s.cloudProvider === stage.cloudProviderType || (s.providesFor || []).includes(stage.cloudProviderType),
      );
      if (stageProvider) {
        $scope.stage.type = stageProvider.key || $scope.stage.type;
        const el = document.querySelector('.react-stage-details');
        if (reactMounted) {
          ReactDOM.unmountComponentAtNode(el);
        }
        if (stageProvider.component) {
          const props = $scope.reactPropsForBaseProviderStage;
          props.component = stageProvider.component;
          $timeout(() => {
            ReactDOM.render(
              React.createElement(StageConfigWrapper, props),
              el || document.querySelector('.react-stage-details'),
            );
          }, 0);
        } else {
          $scope.providerStageDetailsUrl = stageProvider.templateUrl;
        }
        reactMounted = !!stageProvider.component;
      }
    }

    $scope.$watch('stage.cloudProviderType', loadProvider);
  },
]);
