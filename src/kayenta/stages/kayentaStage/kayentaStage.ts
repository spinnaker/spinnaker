import { IComponentController, module } from 'angular';
import { isString } from 'lodash';

import {
  ACCOUNT_SERVICE, CLOUD_PROVIDER_REGISTRY,
  PipelineConfigProvider
} from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';

interface IKayentaStage {
  canaryConfig: IKayentaStageCanaryConfig;
}

interface IKayentaStageCanaryConfig {
  beginCanaryAnalysisAfterMins: string;
  canaryAnalysisIntervalMins: string;
  canaryConfigId: string;
  controlScope: string,
  combinedCanaryResultStrategy: string;
  experimentScope: string;
  lifetimeHours: string;
  lookbackMins?: string;
  metricsAccountName: string;
  storageAccountName: string;
}

class CanaryStage implements IComponentController {

  public state = { useLookback: false };

  constructor(public stage: IKayentaStage) {
    'ngInject';
    this.initialize();
  }

  public onUseLookbackChange(): void {
    if (!this.state.useLookback) {
      delete this.stage.canaryConfig.lookbackMins;
    }
  }

  public isExpression(val: number | string): boolean {
    return isString(val) && val.includes('${');
  }

  private initialize(): void {
    this.stage.canaryConfig = this.stage.canaryConfig || {} as IKayentaStageCanaryConfig;
    this.stage.canaryConfig.storageAccountName =
      this.stage.canaryConfig.storageAccountName || CanarySettings.storageAccountName;
    this.stage.canaryConfig.metricsAccountName =
      this.stage.canaryConfig.metricsAccountName || CanarySettings.metricsAccountName;
    this.stage.canaryConfig.combinedCanaryResultStrategy =
      this.stage.canaryConfig.combinedCanaryResultStrategy || 'LOWEST';

    if (this.stage.canaryConfig.lookbackMins) {
      this.state.useLookback = true;
    }
  }
}

export const KAYENTA_CANARY_STAGE = 'spinnaker.kayenta.canaryStage';
module(KAYENTA_CANARY_STAGE, [
    CLOUD_PROVIDER_REGISTRY,
    ACCOUNT_SERVICE,
  ])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      label: 'Canary',
      description: 'Runs a canary task',
      key: 'kayentaCanary',
      templateUrl: require('./kayentaStage.html'),
      controller: 'KayentaCanaryStageCtrl',
      controllerAs: 'kayentaCanaryStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'canaryConfig.lifetimeHours', fieldLabel: 'Lifetime' },
        { type: 'requiredField', fieldName: 'canaryConfig.canaryConfigId', fieldLabel: 'Config Name' },
        { type: 'requiredField', fieldName: 'canaryConfig.controlScope', fieldLabel: 'Baseline Scope' },
        { type: 'requiredField', fieldName: 'canaryConfig.experimentScope', fieldLabel: 'Canary Scope' },
      ]
    });
  })
  .controller('KayentaCanaryStageCtrl', CanaryStage);
