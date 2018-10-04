import { module } from 'angular';
import { get, has, isEmpty, map, uniq, difference } from 'lodash';

import { IPipeline, Registry } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { getCanaryConfigById } from 'kayenta/service/canaryConfig.service';
import { IKayentaStage, KayentaAnalysisType } from 'kayenta/domain';
import { CANARY_SCORES_CONFIG_COMPONENT } from 'kayenta/components/canaryScores.component';
import { KayentaStageTransformer, KAYENTA_STAGE_TRANSFORMER } from './kayentaStage.transformer';
import { KayentaStageController } from './kayentaStage.controller';
import { KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER } from './kayentaStageExecutionDetails.controller';
import { KAYENTA_STAGE_CONFIG_SECTION } from './kayentaStageConfigSection.component';
import { KAYENTA_ANALYSIS_TYPE_COMPONENT } from './analysisType.component';
import { FOR_ANALYSIS_TYPE_COMPONENT } from './forAnalysisType.component';

const requiredForAnalysisTypes = (
  analysisTypes: KayentaAnalysisType[] = [],
  fieldName: string,
  fieldLabel?: string,
): ((p: IPipeline, s: IKayentaStage) => string) => {
  return (_pipeline: IPipeline, stage: IKayentaStage): string => {
    if (analysisTypes.includes(stage.analysisType)) {
      if (!has(stage, fieldName) || get(stage, fieldName) === '') {
        return `<strong>${fieldLabel || fieldName}</strong> is a required field for Kayenta Canary stages.`;
      }
    }
    return null;
  };
};

const allScopesMustBeConfigured = (_pipeline: IPipeline, stage: IKayentaStage): Promise<string> => {
  return getCanaryConfigById(get(stage, 'canaryConfig.canaryConfigId')).then(configDetails => {
    let definedScopeNames = uniq(map(configDetails.metrics, metric => metric.scopeName || 'default'));
    definedScopeNames = !isEmpty(definedScopeNames) ? definedScopeNames : ['default'];

    const configureScopedNames: string[] = map(get(stage, 'canaryConfig.scopes'), 'scopeName');
    const missingScopeNames = difference(definedScopeNames, configureScopedNames);

    if (missingScopeNames.length > 1) {
      return `Scopes <strong>${missingScopeNames.join()}</strong> are defined but not configured.`;
    } else if (missingScopeNames.length === 1) {
      return `Scope <strong>${missingScopeNames[0]}</strong> is defined but not configured.`;
    } else {
      return null;
    }
  });
};

const allConfiguredScopesMustBeDefined = (_pipeline: IPipeline, stage: IKayentaStage): Promise<string> => {
  return getCanaryConfigById(get(stage, 'canaryConfig.canaryConfigId')).then(configDetails => {
    let definedScopeNames = uniq(map(configDetails.metrics, metric => metric.scopeName || 'default'));
    definedScopeNames = !isEmpty(definedScopeNames) ? definedScopeNames : ['default'];

    const configureScopedNames: string[] = map(get(stage, 'canaryConfig.scopes'), 'scopeName');
    const missingScopeNames = difference(configureScopedNames, definedScopeNames);

    if (missingScopeNames.length > 1) {
      return `Scopes <strong>${missingScopeNames.join()}</strong> are configured but are not defined in the canary configuration.`;
    } else if (missingScopeNames.length === 1) {
      return `Scope <strong>${
        missingScopeNames[0]
      }</strong> is configured but is not defined in the canary configuration.`;
    } else {
      return null;
    }
  });
};

export const KAYENTA_CANARY_STAGE = 'spinnaker.kayenta.canaryStage';
module(KAYENTA_CANARY_STAGE, [
  CANARY_SCORES_CONFIG_COMPONENT,
  KAYENTA_ANALYSIS_TYPE_COMPONENT,
  KAYENTA_STAGE_CONFIG_SECTION,
  KAYENTA_STAGE_TRANSFORMER,
  KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER,
  FOR_ANALYSIS_TYPE_COMPONENT,
])
  .config(() => {
    'ngInject';
    Registry.pipeline.registerStage({
      label: CanarySettings.stageName || 'Canary Analysis',
      description: CanarySettings.stageDescription || 'Runs a canary task',
      key: 'kayentaCanary',
      templateUrl: require('./kayentaStage.html'),
      controller: 'KayentaCanaryStageCtrl',
      controllerAs: 'kayentaCanaryStageCtrl',
      executionDetailsUrl: require('./kayentaStageExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'canaryConfig.canaryConfigId', fieldLabel: 'Config Name' },
        { type: 'requiredField', fieldName: 'canaryConfig.metricsAccountName', fieldLabel: 'Metrics Account' },
        { type: 'requiredField', fieldName: 'canaryConfig.storageAccountName', fieldLabel: 'Storage Account' },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTimeAutomatic],
            'deployments.serverGroupPairs[0].control',
            'Baseline & Canary Server Groups',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTimeAutomatic],
            'deployments.baseline.cluster',
            'Baseline Cluster',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTimeAutomatic],
            'deployments.baseline.account',
            'Baseline Account',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTime, KayentaAnalysisType.Retrospective],
            'canaryConfig.scopes[0].controlScope',
            'Baseline Scope',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTime, KayentaAnalysisType.Retrospective],
            'canaryConfig.scopes[0].experimentScope',
            'Canary Scope',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.RealTime, KayentaAnalysisType.RealTimeAutomatic],
            'canaryConfig.lifetimeDuration',
            'Lifetime',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.Retrospective],
            'canaryConfig.scopes[0].startTimeIso',
            'Start Time',
          ),
        },
        {
          type: 'custom',
          validate: requiredForAnalysisTypes(
            [KayentaAnalysisType.Retrospective],
            'canaryConfig.scopes[0].endTimeIso',
            'End Time',
          ),
        },
        {
          type: 'custom',
          validate: allScopesMustBeConfigured,
        },
        {
          type: 'custom',
          validate: allConfiguredScopesMustBeDefined,
        },
      ],
    });
  })
  .controller('KayentaCanaryStageCtrl', KayentaStageController)
  .run((kayentaStageTransformer: KayentaStageTransformer) => {
    'ngInject';
    Registry.pipeline.registerTransformer(kayentaStageTransformer);
  });
