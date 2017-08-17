import { module, IComponentController, IScope } from 'angular';
import { isString, get } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import {
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
  PipelineConfigProvider
} from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import {
  getCanaryConfigById,
  getCanaryConfigSummaries
} from 'kayenta/service/canaryConfig.service';
import { ICanaryConfig } from 'kayenta/domain/ICanaryConfig';
import { CANARY_SCORES_CONFIG_COMPONENT } from 'kayenta/components/canaryScores.component';

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
  scoreThresholds: {
    pass: string;
    marginal: string;
  };
  storageAccountName: string;
}

@autoBindMethods
class CanaryStage implements IComponentController {

  public state = {
    useLookback: false,
    summariesLoading: false,
    detailsLoading: false
  };
  public canaryConfigNames: string[] = [];
  public selectedCanaryConfigDetails: ICanaryConfig;

  constructor(private $scope: IScope, public stage: IKayentaStage) {
    'ngInject';
    this.initialize();
  }

  public onUseLookbackChange(): void {
    if (!this.state.useLookback) {
      delete this.stage.canaryConfig.lookbackMins;
    }
  }

  public onCanaryConfigSelect(): void {
    this.loadCanaryConfigDetails();
  }

  public isExpression(val: number | string): boolean {
    return isString(val) && val.includes('${');
  }

  public handleScoreThresholdChange(scoreThresholds: { successfulScore: string, unhealthyScore: string }): void {
    // Called from a React component.
    this.$scope.$apply(() => {
      this.stage.canaryConfig.scoreThresholds.pass = scoreThresholds.successfulScore;
      this.stage.canaryConfig.scoreThresholds.marginal = scoreThresholds.unhealthyScore;
    });
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

    this.loadCanaryConfigNames();
  }

  private loadCanaryConfigDetails(): void {
    if (!this.stage.canaryConfig.canaryConfigId) {
      return;
    }

    this.state.detailsLoading = true;
    getCanaryConfigById(this.stage.canaryConfig.canaryConfigId).then(configDetails => {
      this.state.detailsLoading = false;
      this.selectedCanaryConfigDetails = configDetails;
      this.overrideScoreThresholds();
    }).catch(() => {
      this.state.detailsLoading = false;
    });
  }

  private overrideScoreThresholds(): void {
    if (!this.selectedCanaryConfigDetails) {
      return;
    }

    if (!this.stage.canaryConfig.scoreThresholds) {
      this.stage.canaryConfig.scoreThresholds = { marginal: null, pass: null };
    }

    this.stage.canaryConfig.scoreThresholds.marginal = get(
      this.selectedCanaryConfigDetails, 'classifier.scoreThresholds.marginal',
      this.stage.canaryConfig.scoreThresholds.marginal || ''
    ).toString();
    this.stage.canaryConfig.scoreThresholds.pass = get(
      this.selectedCanaryConfigDetails, 'classifier.scoreThresholds.pass',
      this.stage.canaryConfig.scoreThresholds.pass || ''
    ).toString();
  }

  private loadCanaryConfigNames(): void {
    this.state.summariesLoading = true;
    getCanaryConfigSummaries().then(summaries => {
      this.state.summariesLoading = false;
      this.canaryConfigNames = summaries.map(summary => summary.name);
    }).catch(() => {
      this.state.summariesLoading = false;
    });
  }
}

export const KAYENTA_CANARY_STAGE = 'spinnaker.kayenta.canaryStage';
module(KAYENTA_CANARY_STAGE, [
    ACCOUNT_SERVICE,
    CANARY_SCORES_CONFIG_COMPONENT,
    CLOUD_PROVIDER_REGISTRY,
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
