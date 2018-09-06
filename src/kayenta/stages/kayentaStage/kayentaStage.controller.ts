import { IComponentController, ILogService, IScope } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep, first, get, has, isEmpty, isFinite, isString, isNil, map, set, uniq } from 'lodash';
import {
  AccountService, AppListExtractor, CloudProviderRegistry, IAccountDetails,
  NameUtils, ProviderSelectionService, ServerGroupCommandBuilderService
} from '@spinnaker/core';
import { CanarySettings } from 'kayenta/canary.settings';
import { getCanaryConfigById, listKayentaAccounts, } from 'kayenta/service/canaryConfig.service';
import {
  ICanaryConfig,
  ICanaryConfigSummary,
  IKayentaAccount,
  IKayentaServerGroupPair,
  IKayentaStage,
  IKayentaStageCanaryConfigScope,
  IKayentaStageLifetime,
  KayentaAccountType,
  KayentaAnalysisType
} from 'kayenta/domain';

export class KayentaStageController implements IComponentController {
  public state = {
    useLookback: false,
    backingDataLoading: false,
    detailsLoading: false,
    lifetimeHoursUpdatedToDuration: false,
    lifetime: { hours: '', minutes: '' },
  };
  public canaryConfigSummaries: ICanaryConfigSummary[] = [];
  public selectedCanaryConfigDetails: ICanaryConfig;
  public scopeNames: string[] = [];
  public kayentaAccounts = new Map<KayentaAccountType, IKayentaAccount[]>();
  public metricStore: string;
  public providers: string[];
  public accounts: IAccountDetails[];
  public clusterList: string[];

  constructor(
    private $scope: IScope,
    private $uibModal: IModalService,
    private $log: ILogService,
    private providerSelectionService: ProviderSelectionService,
    private serverGroupCommandBuilder: ServerGroupCommandBuilderService,
    private serverGroupTransformer: any,
    public stage: IKayentaStage,
  ) {
    'ngInject';
    this.initialize();
  }

  private initialize = async (): Promise<void> => {
    await this.loadBackingData();

    this.stage.canaryConfig = {
      storageAccountName: first(this.kayentaAccounts.get(KayentaAccountType.ConfigurationStore)) || CanarySettings.storageAccountName,
      metricsAccountName: first(this.kayentaAccounts.get(KayentaAccountType.MetricsStore)) || CanarySettings.metricsAccountName,
      ...this.stage.canaryConfig,
      scoreThresholds: {
        marginal: null,
        pass: null,
        ...get(this.stage, 'canaryConfig.scoreThresholds'),
      },
    };

    this.stage.analysisType =
      this.stage.analysisType || KayentaAnalysisType.RealTimeAutomatic;

    if (!this.stage.canaryConfig.scopes || !this.stage.canaryConfig.scopes.length) {
      this.stage.canaryConfig.scopes =
        [{ scopeName: 'default' } as IKayentaStageCanaryConfigScope];
    }

    const stageLifetime = this.getLifetimeFromStageLifetimeDuration();
    if (!isNil(stageLifetime.hours)) {
      this.state.lifetime.hours = String(stageLifetime.hours);
    }
    if (!isNil(stageLifetime.minutes)) {
      this.state.lifetime.minutes = String(stageLifetime.minutes);
    }

    if (this.stage.canaryConfig.lookbackMins) {
      this.state.useLookback = true;
    }

    this.populateScopeNameChoices();
    this.setMetricStore();
    this.setClusterList();
    this.deleteConfigAccountsIfMissing();
    this.updateLifetimeFromHoursToDuration();
    this.deleteCanaryConfigIdIfMissing();

    if (this.stage.isNew
        && this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
      await this.initializeRealTimeAutomaticAnalysisType();
    }
  };

  private loadBackingData = async (): Promise<void> => {
    this.state.backingDataLoading = true;
    try {
      await this.$scope.application.ready();
      [
        this.selectedCanaryConfigDetails,
        this.kayentaAccounts,
        this.providers,
        this.accounts,
        this.canaryConfigSummaries,
      ] = await Promise.all([
        this.loadCanaryConfigDetails(),
        this.loadKayentaAccounts(),
        this.loadProviders(),
        this.loadAccounts(),
        Promise.resolve(this.$scope.application.getDataSource('canaryConfigs').data)
      ]);
    } catch (e) {
      this.$log.warn('Error loading backing data for Kayenta stage: ', e);
    } finally {
      this.state.backingDataLoading = false;

      // This is the price we pay for async/await, since we're not using $q.
      this.$scope.$applyAsync();
    }
  };

  public onUseLookbackChange = (): void => {
    if (!this.state.useLookback) {
      delete this.stage.canaryConfig.lookbackMins;
    }
  };

  public onCanaryConfigSelect = async (): Promise<void> => {
    this.selectedCanaryConfigDetails = await this.loadCanaryConfigDetails();
    this.populateScopeNameChoices();
    this.setMetricStore();
    this.overrideScoreThresholds();
  };

  public isExpression = (val: number | string): boolean => {
    return isString(val) && val.includes('${');
  };

  public handleScoreThresholdChange = (
    scoreThresholds: { successfulScore: string, unhealthyScore: string }
  ): void => {
    // Called from a React component.
    this.$scope.$applyAsync(() => {
      this.stage.canaryConfig.scoreThresholds.pass = scoreThresholds.successfulScore;
      this.stage.canaryConfig.scoreThresholds.marginal = scoreThresholds.unhealthyScore;
    });
  };

  public handleAnalysisTypeChange = async (type: KayentaAnalysisType): Promise<void> => {
    this.stage.analysisType = type;

    switch (this.stage.analysisType) {
      case KayentaAnalysisType.RealTime:
        delete this.stage.canaryConfig.scopes[0].startTimeIso;
        delete this.stage.canaryConfig.scopes[0].endTimeIso;
        delete this.stage.deployments;
        break;
      case KayentaAnalysisType.RealTimeAutomatic:
        delete this.stage.canaryConfig.scopes[0].startTimeIso;
        delete this.stage.canaryConfig.scopes[0].endTimeIso;
        this.initializeRealTimeAutomaticAnalysisType();
        break;
      case KayentaAnalysisType.Retrospective:
        delete this.stage.canaryConfig.beginCanaryAnalysisAfterMins;
        delete this.stage.canaryConfig.lifetimeDuration;
        delete this.stage.deployments;
        break;
    }
    // Called from React.
    this.$scope.$applyAsync();
  };

  private initializeRealTimeAutomaticAnalysisType = async (): Promise<void> => {
    this.stage.deployments = {
      ...this.stage.deployments,
      baseline: {
        ...(get(this.stage, 'deployments.baseline')),
        cloudProvider: this.providers[0],
        application: this.$scope.application.name,
        cluster: null,
        account: null,
      },
      serverGroupPairs: [],
      delayBeforeCleanup: 0,
    };
    this.accounts = await this.loadAccounts();
    this.setClusterList();
  };

  private loadCanaryConfigDetails = async (): Promise<ICanaryConfig> => {
    if (!has(this.stage, 'canaryConfig.canaryConfigId')) {
      return null;
    }
    const id = this.stage.canaryConfig.canaryConfigId;

    this.state.detailsLoading = true;
    try {
      return getCanaryConfigById(id);
    } catch (e) {
      this.$log.warn(`Could not load canary config with id ${id}: `, e);
      return null;
    } finally {
      this.state.detailsLoading = false;
    }
  };

  private setMetricStore = (): void => {
    this.metricStore = get(
      this.selectedCanaryConfigDetails,
      'metrics[0].query.type'
    );
  };

  // Should only be called when selecting a canary config.
  // Expected stage behavior:
  // On stage load, use the stage's score thresholds rather than the canary config's
  // thresholds.
  // When selecting a canary config, set the stage's thresholds equal
  // to the canary config's thresholds unless they are undefined.
  // In that case, fall back on the stage's thresholds.
  private overrideScoreThresholds = (): void => {
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
  };

  private populateScopeNameChoices = (): void => {
    if (!this.selectedCanaryConfigDetails) {
      return;
    }

    const scopeNames =
      uniq(map(this.selectedCanaryConfigDetails.metrics, metric => metric.scopeName || 'default'));
    this.scopeNames = !isEmpty(scopeNames) ? scopeNames : ['default'];

    if (!isEmpty(this.stage.canaryConfig.scopes) && !scopeNames.includes(this.stage.canaryConfig.scopes[0].scopeName)) {
      delete this.stage.canaryConfig.scopes[0].scopeName;
    } else if (isEmpty(this.stage.canaryConfig.scopes)) {
      this.stage.canaryConfig.scopes = [{ scopeName: scopeNames[0] }] as IKayentaStageCanaryConfigScope[];
    }
  };

  private loadKayentaAccounts = async (): Promise<Map<KayentaAccountType, IKayentaAccount[]>> => {
    const mapped = new Map<KayentaAccountType, IKayentaAccount[]>();
    const accounts = await listKayentaAccounts();

    accounts.forEach(account => {
      account.supportedTypes.forEach(type => {
        if (mapped.has(type)) {
          mapped.set(type, mapped.get(type).concat([account]));
        } else {
          mapped.set(type, [account]);
        }
      });
    });

    return mapped;
  };

  private deleteConfigAccountsIfMissing = (): void => {
    if ((this.kayentaAccounts.get(KayentaAccountType.ObjectStore) || [])
          .every(account => account.name !== this.stage.canaryConfig.storageAccountName)) {
      delete this.stage.canaryConfig.storageAccountName;
    }
    if ((this.kayentaAccounts.get(KayentaAccountType.MetricsStore) || [])
          .every(account => account.name !== this.stage.canaryConfig.metricsAccountName)) {
      delete this.stage.canaryConfig.metricsAccountName;
    }
  };

  private deleteCanaryConfigIdIfMissing = (): void => {
    if (this.canaryConfigSummaries
        .every(s => s.id !== this.stage.canaryConfig.canaryConfigId)) {
      delete this.stage.canaryConfig.canaryConfigId;
    }
  };

  public populateScopeWithExpressions = (): void => {
    this.stage.canaryConfig.scopes[0].controlScope =
      '${ #stage(\'Clone Server Group\')[\'context\'][\'source\'][\'serverGroupName\'] }';
    this.stage.canaryConfig.scopes[0].controlLocation =
      '${ deployedServerGroups[0].region }';
    this.stage.canaryConfig.scopes[0].experimentScope =
      '${ deployedServerGroups[0].serverGroup }';
    this.stage.canaryConfig.scopes[0].experimentLocation =
      '${ deployedServerGroups[0].region }';
  };

  public onLifetimeChange = (): void => {
    const { hours, minutes } = this.getStateLifetime();
    this.stage.canaryConfig.lifetimeDuration = `PT${hours}H${minutes}M`;
  };

  private updateLifetimeFromHoursToDuration = (): void => {
    if (has(this.stage, ['canaryConfig', 'lifetimeHours'])) {
      const hours = parseInt(this.stage.canaryConfig.lifetimeHours, 10);
      if (isFinite(hours)) {
        const fractional =
          parseFloat(this.stage.canaryConfig.lifetimeHours) - hours;
        const minutes = Math.floor(fractional * 60);
        this.stage.canaryConfig.lifetimeDuration = `PT${hours}H`;
        if (isFinite(minutes)) {
          this.stage.canaryConfig.lifetimeDuration += `${minutes}M`;
        }
        this.state.lifetimeHoursUpdatedToDuration = true;
      }
      delete this.stage.canaryConfig.lifetimeHours;
    }
  };

  private getStateLifetime = (): IKayentaStageLifetime => {
    let hours = parseInt(this.state.lifetime.hours, 10);
    let minutes = parseInt(this.state.lifetime.minutes, 10);
    if (!isFinite(hours) || hours < 0) {
      hours = 0;
    }
    if (!isFinite(minutes) || minutes < 0) {
      minutes = 0;
    }
    return { hours, minutes };
  };

  private getLifetimeFromStageLifetimeDuration = (): IKayentaStageLifetime => {
    const duration = get(this.stage, ['canaryConfig', 'lifetimeDuration']);
    if (!isString(duration)) {
      return {};
    }
    const lifetimeComponents = duration.match(/PT(\d+)H(?:(\d+)M)?/i);
    if (lifetimeComponents == null) {
      return {};
    }
    const hours = parseInt(lifetimeComponents[1], 10);
    if (!isFinite(hours) || hours < 0) {
      return {};
    }
    let minutes = parseInt(lifetimeComponents[2], 10);
    if (!isFinite(minutes) || minutes < 0) {
      minutes = 0;
    }
    return { hours, minutes };
  };

  public isLifetimeRequired = (): boolean => {
    const lifetime = this.getStateLifetime();
    return lifetime.hours === 0 && lifetime.minutes === 0;
  };

  public getLifetimeClassnames = (): string => {
    if (this.state.lifetimeHoursUpdatedToDuration) {
      return 'alert alert-warning';
    }
    return '';
  };

  private loadProviders = async (): Promise<string[]> => {
    const providers = await AccountService.listProviders(this.$scope.application);
    // TODO: Open up to all providers.
    return this.providers = providers.filter(p => ['gce', 'aws', 'titus'].includes(p));;
  };

  public handleProviderChange = async (): Promise<void> => {
    set(this.stage, 'deployments.baseline.account', null);
    set(this.stage, 'deployments.baseline.cluster', null);
    this.accounts = await this.loadAccounts();
    this.setClusterList();
  };

  public loadAccounts = (): Promise<IAccountDetails[]> => {
    if (!has(this.stage, 'deployments.baseline.cloudProvider')) {
      return null;
    }

    return Promise.resolve(AccountService.listAccounts(this.stage.deployments.baseline.cloudProvider));
  };

  private setClusterList = (): void => {
    this.clusterList =
      AppListExtractor.getClusters([this.$scope.application], sg =>
        has(this.stage, 'deployments.baseline.account')
          ? sg.account === this.stage.deployments.baseline.account
          : true
      );
  };

  public getRegion = (serverGroup: any): string => {
    if (serverGroup.region) {
      return serverGroup.region;
    }
    const availabilityZones = serverGroup.availabilityZones;
    return availabilityZones
      ? Object.keys(availabilityZones).length
        ? Object.keys(availabilityZones)[0]
        : 'n/a'
      : 'n/a';
  };

  public getServerGroupName = (serverGroup: any): string => {
    return NameUtils.getClusterName(
      serverGroup.application,
      serverGroup.stack,
      serverGroup.freeFormDetails);
  };

  public addPair = async (): Promise<void> => {
    this.stage.deployments.serverGroupPairs = this.stage.deployments.serverGroupPairs || [];
    const provider =
      await (
        has(this.stage, 'deployments.baseline.cloudProvider')
          ? Promise.resolve(this.stage.deployments.baseline.cloudProvider)
          : Promise.resolve(this.providerSelectionService.selectProvider(this.$scope.application, 'serverGroup'))
      );
    this.stage.deployments.baseline.cloudProvider = provider;
    const config = CloudProviderRegistry.getValue(provider, 'serverGroup');

    const command: any =
      await this.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(
        provider,
        null,
        null
      );

    command.viewState = {
      ...command.viewState,
      requiresTemplateSelection: true,
      disableStrategySelection: true,
      hideClusterNamePreview: true,
      readOnlyFields: {
        credentials: true,
        region: true,
        subnet: true,
        useSourceCapacity: true
      },
      overrides: {
        capacity: {
          min: 1,
          max: 1,
          desired: 1,
        },
        useSourceCapacity: false,
      },
    };

    delete command.strategy;
    try {
      const title = 'Add Baseline + Canary Pair';
      const application = this.$scope.application;

      let result;
      if (config.CloneServerGroupModal) {
        // react
        result = await config.CloneServerGroupModal.show({
          title,
          application,
          command,
        });
      } else {
        // angular
        result = await this.$uibModal.open({
          templateUrl: config.cloneServerGroupTemplateUrl,
          controller: `${config.cloneServerGroupController} as ctrl`,
          size: 'lg',
          resolve: {
            title: () => title,
            application: () => application,
            serverGroupCommand: () => command,
          },
        }).result;
      }
      const control = this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result),
        experiment = cloneDeep(control);

      const cleanup = (serverGroup: any, type: string) => {
        delete serverGroup.backingData;
        if (serverGroup.freeFormDetails
            && serverGroup.freeFormDetails.split('-').pop() === type) {
          return;
        }
        serverGroup.freeFormDetails =
          `${serverGroup.freeFormDetails
            ? `${serverGroup.freeFormDetails}-`
            : ''}${type}`;
      };

      cleanup(control, 'control');
      cleanup(experiment, 'experiment');
      this.stage.deployments.serverGroupPairs = [{ control, experiment }];
    } catch (e) {
      this.$log.warn('Error creating server group pair for Kayenta stage: ', e)
    }
  };

  public editServerGroup = async (
    serverGroup: any,
    index: number,
    type: keyof IKayentaServerGroupPair
  ): Promise<void> => {
    serverGroup.provider = serverGroup.provider || serverGroup.cloudProvider;
    const config = CloudProviderRegistry.getValue(serverGroup.cloudProvider, 'serverGroup');
    try {
      const title = `Configure ${type[0].toUpperCase() + type.substring(1)} Server Group`
      const application = this.$scope.application;

      const command = await this.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline(
        application,
        serverGroup,
        null,
        null
      );
      command.viewState = {
        ...command.viewState,
        disableStrategySelection: true,
        hideClusterNamePreview: true,
        readOnlyFields: {
          credentials: true,
          region: true,
          subnet: true,
          useSourceCapacity: true
        },
      };
      delete command.strategy;

      let result;
      if (config.CloneServerGroupModal) {
        // react
        result = await config.CloneServerGroupModal.show({
          title,
          application,
          command,
        });
      } else {
        // angular
        result = await this.$uibModal.open({
          templateUrl: config.cloneServerGroupTemplateUrl,
          controller: `${config.cloneServerGroupController} as ctrl`,
          size: 'lg',
          resolve: {
            title: () => title,
            application: () => application,
            serverGroupCommand: () => command,
          },
        }).result;
      }

      this.stage.deployments.serverGroupPairs[index][type] =
        this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result);
    } catch (e) {
      this.$log.warn('Error editing server group pair for Kayenta stage: ', e)
    }
  };

  public deletePair = (index: number): void => {
    (this.stage.deployments.serverGroupPairs || []).splice(index, 1);
  };
}
