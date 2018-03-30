import { module, IComponentController, IScope } from 'angular';
import { isString, get, has, isEmpty, map, uniq, difference } from 'lodash';
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
  scopes: IKayentaStageCanaryConfigScope[];
  combinedCanaryResultStrategy: string;
  lifetimeHours?: string;
  lookbackMins?: string;
  metricsAccountName: string;
  scoreThresholds: {
    pass: string;
    marginal: string;
  };
  storageAccountName: string;
}

interface IKayentaStageCanaryConfigScope {
  scopeName: string;
  controlScope: string,
  controlLocation: string,
  experimentScope: string;
  experimentLocation: string,
  startTimeIso?: string;
  endTimeIso?: string;
  step?: number;
  extendedScopeParams: {[key: string]: string};
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
  public metricStore: string;

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
    this.loadCanaryConfigDetails().then(() => this.overrideScoreThresholds());
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
        delete this.stage.canaryConfig.scopes[0].startTimeIso;
        delete this.stage.canaryConfig.scopes[0].endTimeIso;
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

    if (!this.stage.canaryConfig.scopes) {
      this.stage.canaryConfig.scopes = [];
    }

    this.loadBackingData();
  }

  private loadCanaryConfigDetails(): Promise<void> {
    if (!this.stage.canaryConfig.canaryConfigId) {
      return Promise.resolve(null);
    }

    this.state.detailsLoading = true;
    return getCanaryConfigById(this.stage.canaryConfig.canaryConfigId).then(configDetails => {
      this.state.detailsLoading = false;
      this.selectedCanaryConfigDetails = configDetails;
      this.populateScopeNameChoices(configDetails);
      this.metricStore = get(configDetails, 'metrics[0].query.type');
    }).catch(() => {
      this.state.detailsLoading = false;
    });
  }

  // Should only be called when selecting a canary config.
  // Expected stage behavior:
  // On stage load, use the stage's score thresholds rather than the canary config's
  // thresholds.
  // When selecting a canary config, set the stage's thresholds equal
  // to the canary config's thresholds unless they are undefined.
  // In that case, fall back on the stage's thresholds.
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

    if (!isEmpty(this.stage.canaryConfig.scopes) && !scopeNames.includes(this.stage.canaryConfig.scopes[0].scopeName)) {
      delete this.stage.canaryConfig.scopes[0].scopeName;
    } else if (isEmpty(this.stage.canaryConfig.scopes)) {
      this.stage.canaryConfig.scopes = [{ scopeName: scopeNames[0] }] as IKayentaStageCanaryConfigScope[];
    }
  }

  private loadBackingData(): void {
    this.state.backingDataLoading = true;
    Promise.all([
      this.$scope.application.ready().then(() => {
        this.setCanaryConfigSummaries(this.$scope.application.getDataSource('canaryConfigs').data);
        this.deleteCanaryConfigIdIfMissing();
        this.loadCanaryConfigDetails();
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

  public populateScopeWithExpressions(): void {
    this.stage.canaryConfig.scopes[0].controlScope =
      '${ #stage(\'Clone Server Group\')[\'context\'][\'source\'][\'serverGroupName\'] }';
    this.stage.canaryConfig.scopes[0].controlLocation =
      '${ deployedServerGroups[0].region }';
    this.stage.canaryConfig.scopes[0].experimentScope =
      '${ deployedServerGroups[0].serverGroup }';
    this.stage.canaryConfig.scopes[0].experimentLocation =
      '${ deployedServerGroups[0].region }';
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
      return `Scope <strong>${missingScopeNames[0]}</strong> is configured but is not defined in the canary configuration.`;
    } else {
      return null;
    }
  });
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
      label: 'Canary Analysis',
      description: 'Runs a canary task',
      key: 'kayentaCanary',
      templateUrl: require('./kayentaStage.html'),
      controller: 'KayentaCanaryStageCtrl',
      controllerAs: 'kayentaCanaryStageCtrl',
      executionDetailsUrl: require('./kayentaStageExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'canaryConfig.canaryConfigId', fieldLabel: 'Config Name' },
        { type: 'requiredField', fieldName: 'canaryConfig.scopes[0].controlScope', fieldLabel: 'Baseline Scope' },
        { type: 'requiredField', fieldName: 'canaryConfig.scopes[0].experimentScope', fieldLabel: 'Canary Scope' },
        { type: 'requiredField', fieldName: 'canaryConfig.metricsAccountName', fieldLabel: 'Metrics Account' },
        { type: 'requiredField', fieldName: 'canaryConfig.storageAccountName', fieldLabel: 'Storage Account' },
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.RealTime, 'canaryConfig.lifetimeHours', 'Lifetime') },
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.Retrospective, 'canaryConfig.scopes[0].startTimeIso', 'Start Time') },
        { type: 'custom', validate: requiredForAnalysisType(KayentaAnalysisType.Retrospective, 'canaryConfig.scopes[0].endTimeIso', 'End Time') },
        { type: 'custom', validate: allScopesMustBeConfigured },
        { type: 'custom', validate: allConfiguredScopesMustBeDefined },
      ]
    });
  })
  .controller('KayentaCanaryStageCtrl', CanaryStage)
  .run((pipelineConfig: PipelineConfigProvider, kayentaStageTransformer: KayentaStageTransformer) => {
    pipelineConfig.registerTransformer(kayentaStageTransformer);
  });
