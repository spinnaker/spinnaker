import { module, IComponentController, IScope } from 'angular';
import { isString, get, has, isEmpty, map, uniq } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import {
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY, IPipeline,
  PipelineConfigProvider
} from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import {
  getCanaryConfigById,
  listKayentaAccounts,
} from 'kayenta/service/canaryConfig.service';
import { ICanaryConfig, ICanaryConfigSummary, IKayentaAccount, KayentaAccountType } from 'kayenta/domain/index';
import { CANARY_SCORES_CONFIG_COMPONENT } from 'kayenta/components/canaryScores.component';
import { KayentaStageTransformer, KAYENTA_STAGE_TRANSFORMER } from './kayentaStage.transformer';
import { KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER } from './kayentaStageExecutionDetails.controller';
import { KAYENTA_STAGE_CONFIG_SECTION } from './kayentaStageConfigSection.component';

interface IKayentaStage {
  canaryConfig: IKayentaStageCanaryConfig;
  analysisType: KayentaAnalysisType;
}

interface IKayentaStageCanaryConfig {
  beginCanaryAnalysisAfterMins?: string;
  canaryAnalysisIntervalMins: string;
  canaryConfigId: string;
  controlScope: string,
  combinedCanaryResultStrategy: string;
  endTimeIso?: string;
  experimentScope: string;
  lifetimeHours?: string;
  lookbackMins?: string;
  metricsAccountName: string;
  scoreThresholds: {
    pass: string;
    marginal: string;
  };
  startTimeIso?: string;
  step?: string;
  storageAccountName: string;
  scopeName: string;
}

enum KayentaAnalysisType {
  RealTime = 'realTime',
  Retrospective = 'retrospective',
}

@autoBindMethods
class CanaryStage implements IComponentController {

  public state = {
    useLookback: false,
    backingDataLoading: false,
    detailsLoading: false,
  };
  public canaryConfigSummaries: ICanaryConfigSummary[] = [];
  public selectedCanaryConfigDetails: ICanaryConfig;
  public scopeNames: string[] = [];
  public kayentaAccounts = new Map<KayentaAccountType, IKayentaAccount[]>();

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

  public handleAnalysisTypeChange(): void {
    switch (this.stage.analysisType) {
      case KayentaAnalysisType.RealTime:
        delete this.stage.canaryConfig.startTimeIso;
        delete this.stage.canaryConfig.endTimeIso;
        break;
      case KayentaAnalysisType.Retrospective:
        delete this.stage.canaryConfig.beginCanaryAnalysisAfterMins;
        delete this.stage.canaryConfig.lifetimeHours;
        break;
    }
  }

  private initialize(): void {
    this.stage.canaryConfig = this.stage.canaryConfig || {} as IKayentaStageCanaryConfig;
    this.stage.canaryConfig.storageAccountName =
      this.stage.canaryConfig.storageAccountName || CanarySettings.storageAccountName;
    this.stage.canaryConfig.metricsAccountName =
      this.stage.canaryConfig.metricsAccountName || CanarySettings.metricsAccountName;
    this.stage.canaryConfig.combinedCanaryResultStrategy =
      this.stage.canaryConfig.combinedCanaryResultStrategy || 'LOWEST';
    this.stage.analysisType =
      this.stage.analysisType || KayentaAnalysisType.RealTime;

    if (this.stage.canaryConfig.lookbackMins) {
      this.state.useLookback = true;
    }

    this.loadBackingData();
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
      this.populateScopeNameChoices(configDetails);
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

  private populateScopeNameChoices(configDetails: ICanaryConfig): void {
    const scopeNames = uniq(map(configDetails.metrics, metric => metric.scopeName || 'default'));
    this.scopeNames = !isEmpty(scopeNames) ? scopeNames : ['default'];
    if (!scopeNames.includes(this.stage.canaryConfig.scopeName)) {
      delete this.stage.canaryConfig.scopeName;
    }
  }

  private loadBackingData(): void {
    this.state.backingDataLoading = true;
    Promise.all([
      this.$scope.application.ready().then(() => {
        this.setCanaryConfigSummaries(this.$scope.application.getDataSource('canaryConfigs').data);
        this.deleteCanaryConfigIdIfMissing();
        this.loadCanaryConfigDetailsIfPresent();
      }),
      listKayentaAccounts().then(this.setKayentaAccounts).then(this.deleteConfigAccountsIfMissing),
    ]).then(() => this.state.backingDataLoading = false)
      .catch(() => this.state.backingDataLoading = false);
  }

  private setKayentaAccounts(accounts: IKayentaAccount[]): void {
    accounts.forEach(account => {
      account.supportedTypes.forEach(type => {
        if (this.kayentaAccounts.has(type)) {
          this.kayentaAccounts.set(type, this.kayentaAccounts.get(type).concat([account]));
        } else {
          this.kayentaAccounts.set(type, [account]);
        }
      });
    });
  }

  private deleteConfigAccountsIfMissing(): void {
    if ((this.kayentaAccounts.get(KayentaAccountType.ObjectStore) || [])
          .every(account => account.name !== this.stage.canaryConfig.storageAccountName)) {
      delete this.stage.canaryConfig.storageAccountName;
    }
    if ((this.kayentaAccounts.get(KayentaAccountType.MetricsStore) || [])
          .every(account => account.name !== this.stage.canaryConfig.metricsAccountName)) {
      delete this.stage.canaryConfig.metricsAccountName;
    }
  }

  private setCanaryConfigSummaries(summaries: ICanaryConfigSummary[]): void {
    this.canaryConfigSummaries = summaries;
  }

  private deleteCanaryConfigIdIfMissing(): void {
    if (this.canaryConfigSummaries.every(s => s.id !== this.stage.canaryConfig.canaryConfigId)) {
      delete this.stage.canaryConfig.canaryConfigId;
    }
  }

  private loadCanaryConfigDetailsIfPresent(): void {
    if (!this.stage.canaryConfig.canaryConfigId) {
      return;
    }

    this.state.detailsLoading = true;
    getCanaryConfigById(this.stage.canaryConfig.canaryConfigId).then(configDetails => {
      this.state.detailsLoading = false;
      this.populateScopeNameChoices(configDetails);
    }).catch(() => {
      this.state.detailsLoading = false;
    });
  }
}

const requiredForAnalysisType = (analysisType: KayentaAnalysisType, fieldName: string, fieldLabel?: string): (p: IPipeline, s: IKayentaStage) => string => {
  return (_pipeline: IPipeline, stage: IKayentaStage): string => {
    if (stage.analysisType === analysisType) {
      if (!has(stage, fieldName) || get(stage, fieldName) === '') {
        return `<strong>${fieldLabel || fieldName}</strong> is a required field for Kayenta Canary stages.`;
      }
    }
    return null;
  }
};

export const KAYENTA_CANARY_STAGE = 'spinnaker.kayenta.canaryStage';
module(KAYENTA_CANARY_STAGE, [
    ACCOUNT_SERVICE,
    CANARY_SCORES_CONFIG_COMPONENT,
    CLOUD_PROVIDER_REGISTRY,
    KAYENTA_STAGE_CONFIG_SECTION,
    KAYENTA_STAGE_TRANSFORMER,
    KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER,
  ])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      label: 'Canary',
      description: 'Runs a canary task',
      key: 'kayentaCanary',
      templateUrl: require('./kayentaStage.html'),
      controller: 'KayentaCanaryStageCtrl',
      controllerAs: 'kayentaCanaryStageCtrl',
      executionDetailsUrl: require('./kayentaStageExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'canaryConfig.canaryConfigId', fieldLabel: 'Config Name' },
        { type: 'requiredField', fieldName: 'canaryConfig.controlScope', fieldLabel: 'Baseline Scope' },
        { type: 'requiredField', fieldName: 'canaryConfig.experimentScope', fieldLabel: 'Canary Scope' },
        { type: 'requiredField', fieldName: 'canaryConfig.metricsAccountName', fieldLabel: 'Metrics Account'},
        { type: 'requiredField', fieldName: 'canaryConfig.storageAccountName', fieldLabel: 'Storage Account'},
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.RealTime, 'canaryConfig.lifetimeHours', 'Lifetime')},
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.Retrospective, 'canaryConfig.startTimeIso', 'Start Time')},
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.Retrospective, 'canaryConfig.endTimeIso', 'End Time')},
      ]
    });
  })
  .controller('KayentaCanaryStageCtrl', CanaryStage)
  .run((pipelineConfig: PipelineConfigProvider, kayentaStageTransformer: KayentaStageTransformer) => {
    pipelineConfig.registerTransformer(kayentaStageTransformer);
  });
