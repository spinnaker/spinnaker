import { CanarySettings } from 'kayenta/canary.settings';
import { IKayentaStage, KayentaAnalysisType } from 'kayenta/domain';
import { getCanaryConfigById } from 'kayenta/service/canaryConfig.service';
import { difference, get, has, isEmpty, isString, map, uniq } from 'lodash';

import { IPipeline } from '@spinnaker/core';

import { CanaryExecutionLabel } from './CanaryExecutionLabel';

const isExpression = (value: string) => isString(value) && value.includes('${');

const emailPattern = /^(.+)@(.+).([A-Za-z]{2,6})/;
const isValidEmail = (email: string) => isExpression(email) || email.match(emailPattern);

const utcInstantPattern = /^(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[01]|0[1-9]|[12][0-9])T(2[0-3]|[01][0-9]):([0-5][0-9]):([0-5][0-9])(.[0-9]+)?(Z)?$/;
const isValidUtcInstant = (timestamp: string) =>
  isExpression(timestamp) || (isString(timestamp) && timestamp.match(utcInstantPattern));

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

const allScopesMustBeConfigured = (_pipeline: IPipeline, stage: IKayentaStage): PromiseLike<string> => {
  return getCanaryConfigById(get(stage, 'canaryConfig.canaryConfigId')).then((configDetails) => {
    let definedScopeNames = uniq(map(configDetails.metrics, (metric) => metric.scopeName || 'default'));
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

const allConfiguredScopesMustBeDefined = (_pipeline: IPipeline, stage: IKayentaStage): PromiseLike<string> => {
  return getCanaryConfigById(get(stage, 'canaryConfig.canaryConfigId')).then((configDetails) => {
    let definedScopeNames = uniq(map(configDetails.metrics, (metric) => metric.scopeName || 'default'));
    definedScopeNames = !isEmpty(definedScopeNames) ? definedScopeNames : ['default'];

    const configureScopedNames: string[] = map(get(stage, 'canaryConfig.scopes'), 'scopeName');
    const missingScopeNames = difference(configureScopedNames, definedScopeNames);

    if (missingScopeNames.length > 1) {
      return `Scopes <strong>${missingScopeNames.join()}</strong> are configured but are not defined in the canary configuration.`;
    } else if (missingScopeNames.length === 1) {
      return `Scope <strong>${missingScopeNames[0]}</strong> is configured but is not defined in the canary configuration.`;
    } else {
      return null;
    }
  });
};

export const kayentaCanaryStage = {
  label: CanarySettings.stageName || 'Canary Analysis',
  description: CanarySettings.stageDescription || 'Runs a canary task',
  key: 'kayentaCanary',
  templateUrl: require('./kayentaStage.html'),
  controller: 'KayentaCanaryStageCtrl',
  controllerAs: 'kayentaCanaryStageCtrl',
  executionDetailsUrl: require('./kayentaStageExecutionDetails.html'),
  executionLabelComponent: CanaryExecutionLabel,
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
      validate: (_pipeline: IPipeline, stage: IKayentaStage) => {
        if (
          !has(stage, 'canaryConfig.canaryConfigId') ||
          stage.analysisType === KayentaAnalysisType.RealTimeAutomatic
        ) {
          return null;
        }

        return getCanaryConfigById(get(stage, 'canaryConfig.canaryConfigId')).then((configDetails) => {
          if (
            get(configDetails, 'metrics[0].query.type') === 'atlas' &&
            !get(stage, 'canaryConfig.scopes[0].extendedScopeParams.type')
          ) {
            return 'Scope Type is required';
          } else {
            return null;
          }
        });
      },
    },
    {
      type: 'custom',
      validate: allScopesMustBeConfigured,
    },
    {
      type: 'custom',
      validate: allConfiguredScopesMustBeDefined,
    },
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, { canaryConfig }: IKayentaStage) => {
        if (!CanarySettings.legacySiteLocalFieldsEnabled) {
          return null;
        }
        const notificationEmail = get(canaryConfig, 'siteLocal.notificationEmail');
        if (!notificationEmail) {
          return null;
        }
        const emails = Array.isArray(notificationEmail) ? notificationEmail : [notificationEmail];
        const invalidEmail = emails.find((email) => !isValidEmail(email));
        return invalidEmail ? `Invalid <strong>Notification Email</strong> (${invalidEmail})` : null;
      },
    },
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, stage: IKayentaStage) => {
        const startTime: string = get(stage, 'canaryConfig.scopes[0].startTimeIso');
        if (
          stage.analysisType === KayentaAnalysisType.Retrospective &&
          !isEmpty(startTime) &&
          !isValidUtcInstant(startTime)
        ) {
          return '<strong>Start Time</strong> must be formatted as a UTC instant using ISO-8601 instant format (e.g., 2018-07-12T20:28:29Z).';
        }
        return null;
      },
    },
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, stage: IKayentaStage) => {
        const endTime: string = get(stage, 'canaryConfig.scopes[0].endTimeIso');
        if (
          stage.analysisType === KayentaAnalysisType.Retrospective &&
          !isEmpty(endTime) &&
          !isValidUtcInstant(endTime)
        ) {
          return '<strong>End Time</strong> must be formatted as a UTC instant using ISO-8601 instant format (e.g., 2018-07-12T20:28:29Z).';
        }
        return null;
      },
    },
  ],
};
