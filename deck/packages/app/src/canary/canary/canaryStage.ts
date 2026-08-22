import { isString, toInteger } from 'lodash';

import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry, SETTINGS } from '@spinnaker/core';

import { CanaryExecutionConfigDetails, CanaryExecutionDetails } from './CanaryExecutionDetails';
import { CanaryExecutionLabel } from './CanaryExecutionLabel';
import { CanaryExecutionSummary } from './CanaryExecutionSummary';
import { CanaryStageConfig } from './CanaryStageConfig';
import { canaryStageTransformer } from './canaryStage.transformer';

function isExpression(value: any) {
  return isString(value) && value.includes('${');
}

function isValidValue(value: any, min = 0) {
  let result = false;
  if (isExpression(value) || (!isExpression(value) && toInteger(value) > min)) {
    result = true;
  }

  return result;
}

Registry.pipeline.registerTransformer(canaryStageTransformer);

if (SETTINGS.feature.canary) {
  Registry.pipeline.registerStage({
    label: 'Canary',
    description: 'Canary tests new changes against a baseline version',
    extendedDescription: SETTINGS.canaryDocumentationUrl
      ? `<a target="_blank" href="${SETTINGS.canaryDocumentationUrl}">
          <span class="small glyphicon glyphicon-file"></span> Documentation</a>`
      : undefined,
    key: 'canary',
    cloudProviders: ['aws'],
    component: CanaryStageConfig,
    executionDetailsSections: [CanaryExecutionDetails, CanaryExecutionConfigDetails, ExecutionDetailsTasks],
    executionSummaryComponent: CanaryExecutionSummary,
    executionLabelComponent: CanaryExecutionLabel,
    stageFilter: (stage) => ['canaryDeployment', 'canary'].includes(stage.type),
    accountExtractor: (stage: IStage) => (stage.context.clusterPairs || []).map((c: any) => c.baseline.account),
    configAccountExtractor: (stage: IStage) => (stage.clusterPairs || []).map((c: any) => c.baseline.account),
    validators: [
      {
        type: 'stageBeforeType',
        stageTypes: ['bake', 'findAmi', 'findImage', 'findImageFromTags'],
        message: 'You must have a Bake or Find AMI stage before a canary stage.',
      },
      { type: 'requiredField', fieldName: 'canary.canaryConfig.lifetimeHours', fieldLabel: 'Canary Lifetime' },
      { type: 'requiredField', fieldName: 'baseline.account', fieldLabel: 'Account' },
      { type: 'requiredField', fieldName: 'baseline.cluster', fieldLabel: 'Cluster' },
      { type: 'requiredField', fieldName: 'clusterPairs', fieldLabel: 'Cluster Pairs' },
      {
        type: 'requiredField',
        fieldName: 'canary.canaryConfig.canaryAnalysisConfig.name',
        fieldLabel: 'Configuration',
      },
      {
        type: 'custom',
        fieldLabel: 'Lookback Duration',
        validate: (_pipeline: any, stage: any) => {
          const cac = stage.canary.canaryConfig.canaryAnalysisConfig;
          const useLookback = cac.useLookback;
          const lookbackMins = cac.lookbackMins;
          let result = null;
          if (useLookback && !isValidValue(lookbackMins)) {
            result =
              'When an analysis type of <strong>Sliding Lookback</strong> is selected, the lookback duration must be positive.';
          }

          return result;
        },
      },
      {
        type: 'custom',
        fieldLabel: 'Report Frequency',
        validate: (_pipeline: any, stage: any) => {
          const reportFrequency = stage.canary.canaryConfig.canaryAnalysisConfig.canaryAnalysisIntervalMins;
          let result = null;
          if (!isValidValue(reportFrequency, 0)) {
            result = 'The <strong>Report Frequency</strong> is required and must be positive.';
          }

          return result;
        },
      },
      {
        type: 'custom',
        fieldLabel: 'Warmup Period',
        validate: (_pipeline: any, stage: any) => {
          const warmup = stage.canary.canaryConfig.canaryAnalysisConfig.beginCanaryAnalysisAfterMins;
          let result = null;
          if (warmup && (isNaN(warmup) || parseInt(warmup, 10) < 0)) {
            result = 'When a <strong>Warmup Period</strong> is specified, it must be non-negative.';
          }

          return result;
        },
      },
      {
        type: 'custom',
        fieldLabel: 'Successful Score',
        validate: (_pipeline: any, stage: any) => {
          const unhealthyScore = stage.canary.canaryConfig.canaryHealthCheckHandler.minimumCanaryResultScore;
          const successfulScore = stage.canary.canaryConfig.canarySuccessCriteria.canaryResultScore;
          let result = null;
          if (
            !isValidValue(successfulScore) ||
            (!isExpression(unhealthyScore) && toInteger(unhealthyScore) >= toInteger(successfulScore))
          ) {
            result =
              'The <strong>Successful Score</strong> is required, must be positive, and must be greater than the unhealthy score.';
          }

          return result;
        },
      },
      {
        type: 'custom',
        fieldLabel: 'Unhealthy Score',
        validate: (_pipeline: any, stage: any) => {
          const unhealthyScore = stage.canary.canaryConfig.canaryHealthCheckHandler.minimumCanaryResultScore;
          const successfulScore = stage.canary.canaryConfig.canarySuccessCriteria.canaryResultScore;
          let result = null;
          if (
            !isValidValue(unhealthyScore) ||
            (!isExpression(successfulScore) && toInteger(unhealthyScore) >= toInteger(successfulScore))
          ) {
            result =
              'The <strong>Unhealthy Score</strong> is required, must be positive, and must be less than the successful score.';
          }

          return result;
        },
      },
      {
        type: 'custom',
        validate: (_pipeline: any, stage: any) => {
          let result = null;
          if (stage.scaleUp.enabled) {
            const delay = stage.scaleUp.delay;
            if (!isValidValue(delay, -1)) {
              result = 'When a canary scale-up is enabled, the delay value is required and must be non-negative.';
            }
          }

          return result;
        },
      },
      {
        type: 'custom',
        validate: (_pipeline: any, stage: any) => {
          let result = null;
          if (stage.scaleUp.enabled) {
            const capacity = stage.scaleUp.capacity;
            if (!isValidValue(capacity)) {
              result = 'When a canary scale-up is enabled, the capacity value must be positive.';
            }
          }

          return result;
        },
      },
    ] as any,
  });
}
