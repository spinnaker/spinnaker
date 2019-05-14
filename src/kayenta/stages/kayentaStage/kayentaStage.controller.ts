import { IComponentController, ILogService, IScope } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep, first, get, has, isEmpty, isFinite, isString, map, set, unset, uniq } from 'lodash';
import {
  AccountService,
  AppListExtractor,
  CloudProviderRegistry,
  IAccountDetails,
  NameUtils,
  ProviderSelectionService,
  ServerGroupCommandBuilderService,
} from '@spinnaker/core';
import { CanarySettings } from 'kayenta/canary.settings';
import { getCanaryConfigById, listKayentaAccounts } from 'kayenta/service/canaryConfig.service';
import {
  ICanaryConfig,
  ICanaryConfigSummary,
  IKayentaAccount,
  IKayentaServerGroupPair,
  IKayentaStage,
  IKayentaStageCanaryConfigScope,
  KayentaAccountType,
  KayentaAnalysisType,
} from 'kayenta/domain';
import { getDurationString, parseDurationString } from 'kayenta/utils/duration';

import './kayentaStage.less';

// TODO: Open up to all providers.
const REAL_TIME_AUTOMATIC_PROVIDERS = ['gce', 'aws', 'titus'];

export class KayentaStageController implements IComponentController {
  public static $inject = [
    '$scope',
    '$uibModal',
    '$log',
    'serverGroupCommandBuilder',
    'serverGroupTransformer',
    'stage',
  ];

  public state = {
    useLookback: false,
    backingDataLoading: false,
    lifetimeHoursUpdatedToDuration: false,
    lifetime: { hours: 0, minutes: 0 },
    showAdvancedSettings: false,
    useAtlasGlobalDataset: false,
    atlasScopeType: 'cluster',
    legacySiteLocalRecipients: '',
    showLegacySiteLocalRecipients: CanarySettings.legacySiteLocalFieldsEnabled,
    showAllLocations: {
      control: false,
      experiment: false,
    },
    delayBeforeCleanup: { hours: 0, minutes: 0 },
    analysisTypes: [
      KayentaAnalysisType.RealTimeAutomatic,
      KayentaAnalysisType.RealTime,
      KayentaAnalysisType.Retrospective,
    ],
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
    private serverGroupCommandBuilder: ServerGroupCommandBuilderService,
    private serverGroupTransformer: any,
    public stage: IKayentaStage,
  ) {
    'ngInject';
    this.initialize();
  }

  private initialize = async (): Promise<void> => {
    await this.loadBackingData();

    const firstStorageAccount = first(this.kayentaAccounts.get(KayentaAccountType.ObjectStore));
    const firstMetricsAccount = first(this.kayentaAccounts.get(KayentaAccountType.MetricsStore));

    this.stage.canaryConfig = {
      storageAccountName: (firstStorageAccount && firstStorageAccount.name) || CanarySettings.storageAccountName,
      metricsAccountName: (firstMetricsAccount && firstMetricsAccount.name) || CanarySettings.metricsAccountName,
      ...this.stage.canaryConfig,
      scoreThresholds: {
        marginal: null,
        pass: null,
        ...get(this.stage, 'canaryConfig.scoreThresholds'),
      },
    };

    this.stage.analysisType = this.stage.analysisType || this.state.analysisTypes[0];

    if (!this.stage.canaryConfig.scopes || !this.stage.canaryConfig.scopes.length) {
      this.stage.canaryConfig.scopes = [{ scopeName: 'default' } as IKayentaStageCanaryConfigScope];
    }

    if (!this.stage.isNew) {
      const stageLifetimeDuration: string = get(this.stage, 'canaryConfig.lifetimeDuration');
      this.state.lifetime = parseDurationString(stageLifetimeDuration);

      const stageDelayBeforeCleanupDuration: string = get(this.stage, 'deployments.delayBeforeCleanup');
      this.state.delayBeforeCleanup = parseDurationString(stageDelayBeforeCleanupDuration);
    }

    if (this.stage.canaryConfig.lookbackMins) {
      this.state.useLookback = true;
    }

    this.populateScopeNameChoices();
    this.setMetricStore();
    this.setLocations();
    this.setShowAdvancedSettings();
    this.setClusterList();
    this.deleteConfigAccountsIfMissing();
    this.updateLifetimeFromHoursToDuration();
    this.deleteCanaryConfigIdIfMissing();

    if (CanarySettings.legacySiteLocalFieldsEnabled) {
      this.setLegacySiteLocalRecipients();
    }

    if (this.stage.isNew && this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
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
        Promise.resolve(this.$scope.application.getDataSource('canaryConfigs').data),
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
  };

  public isExpression = (val: number | string): boolean => {
    return isString(val) && val.includes('${');
  };

  public handleScoreThresholdChange = (scoreThresholds: { successfulScore: string; unhealthyScore: string }): void => {
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

        if (this.metricStore === 'atlas') {
          this.stage.canaryConfig.scopes.forEach(scope => {
            unset(scope, 'extendedScopeParams.dataset');
            unset(scope, 'extendedScopeParams.environment');
          });
          this.setAtlasScopeParams();
        }
        break;
      case KayentaAnalysisType.RealTimeAutomatic:
        delete this.stage.canaryConfig.scopes[0].startTimeIso;
        delete this.stage.canaryConfig.scopes[0].endTimeIso;
        delete this.stage.canaryConfig.scopes[0].controlLocation;
        delete this.stage.canaryConfig.scopes[0].experimentLocation;
        this.initializeRealTimeAutomaticAnalysisType();
        break;
      case KayentaAnalysisType.Retrospective:
        delete this.stage.canaryConfig.beginCanaryAnalysisAfterMins;
        delete this.stage.canaryConfig.lifetimeDuration;
        delete this.stage.deployments;

        if (this.metricStore === 'atlas') {
          this.stage.canaryConfig.scopes.forEach(scope => {
            unset(scope, 'extendedScopeParams.dataset');
            unset(scope, 'extendedScopeParams.environment');
          });
          this.setAtlasScopeParams();
        }
        break;
    }
    // Called from React.
    this.$scope.$applyAsync();
  };

  private initializeRealTimeAutomaticAnalysisType = async (): Promise<void> => {
    this.stage.deployments = {
      ...this.stage.deployments,
      baseline: {
        ...get(this.stage, 'deployments.baseline'),
        cloudProvider: this.providers[0],
        application: this.$scope.application.name,
        cluster: null,
        account: null,
      },
      serverGroupPairs: [],
    };
    this.accounts = await this.loadAccounts();
    this.setClusterList();
    this.setMetricStore();
  };

  private loadCanaryConfigDetails = async (): Promise<ICanaryConfig> => {
    if (!has(this.stage, 'canaryConfig.canaryConfigId')) {
      return null;
    }
    const id = this.stage.canaryConfig.canaryConfigId;

    try {
      return getCanaryConfigById(id);
    } catch (e) {
      this.$log.warn(`Could not load canary config with id ${id}: `, e);
      return null;
    }
  };

  private setMetricStore = (): void => {
    this.metricStore = get(this.selectedCanaryConfigDetails, 'metrics[0].query.type');

    if (this.metricStore === 'atlas') {
      this.setAtlasScopeParams();
      this.$scope.$applyAsync();
    } else {
      this.stage.canaryConfig.scopes.forEach(scope => {
        unset(scope, 'extendedScopeParams.type');
      });
    }
  };

  private setAtlasScopeParams = (): void => {
    const isRealTimeAutomatic = this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic;

    const existingScopeType = get(this.stage.canaryConfig.scopes[0], 'extendedScopeParams.type') as string;
    if (!isRealTimeAutomatic) {
      this.state.atlasScopeType = existingScopeType !== 'asg' ? existingScopeType : 'cluster';
    }
    this.stage.canaryConfig.scopes.forEach(scope => {
      set(scope, 'extendedScopeParams.type', isRealTimeAutomatic ? 'asg' : this.state.atlasScopeType);
    });

    if (isRealTimeAutomatic) {
      this.state.useAtlasGlobalDataset =
        get(this.stage.canaryConfig.scopes[0], 'extendedScopeParams.dataset') === 'global';
      this.stage.canaryConfig.scopes.forEach(scope => {
        set(scope, 'extendedScopeParams.dataset', this.state.useAtlasGlobalDataset ? 'global' : 'regional');
      });
    }
  };

  private setLocations = (): void => {
    const { recommendedLocations, locations, hasChoices } = this.getLocationChoices();

    if (!hasChoices) {
      return;
    }

    this.state.showAllLocations.control = this.state.showAllLocations.experiment =
      recommendedLocations.length === 0 && locations.length > 0;

    this.stage.canaryConfig.scopes.forEach(scope => {
      if (!recommendedLocations.includes(scope.controlLocation) && !locations.includes(scope.controlLocation)) {
        delete scope.controlLocation;
      } else if (!recommendedLocations.includes(scope.controlLocation)) {
        this.state.showAllLocations.control = true;
      }

      if (!recommendedLocations.includes(scope.experimentLocation) && !locations.includes(scope.experimentLocation)) {
        delete scope.experimentLocation;
      } else if (!recommendedLocations.includes(scope.experimentLocation)) {
        this.state.showAllLocations.experiment = true;
      }
    });
  };

  private setShowAdvancedSettings = (): void => {
    this.state.showAdvancedSettings =
      this.kayentaAccounts.get(KayentaAccountType.MetricsStore).length > 1 ||
      this.kayentaAccounts.get(KayentaAccountType.ObjectStore).length > 1 ||
      this.scopeNames.length > 1 ||
      CanarySettings.legacySiteLocalFieldsEnabled;
  };

  public getLocationChoices = (): {
    combinedLocations: { control: string[]; experiment: string[] };
    recommendedLocations: string[];
    locations: string[];
    hasChoices: boolean;
  } => {
    const accounts = this.kayentaAccounts.get(KayentaAccountType.MetricsStore);
    const selectedAccount =
      accounts && accounts.find(({ name }) => name === this.stage.canaryConfig.metricsAccountName);
    if (!selectedAccount) {
      return {
        combinedLocations: { control: [], experiment: [] },
        recommendedLocations: [],
        locations: [],
        hasChoices: false,
      };
    }

    const hasChoices = selectedAccount.locations.length > 0 || selectedAccount.recommendedLocations.length > 0;
    const allLocations = selectedAccount.recommendedLocations.concat(selectedAccount.locations);
    const control = uniq(
      this.state.showAllLocations.control
        ? allLocations
        : selectedAccount.recommendedLocations.length > 0
          ? selectedAccount.recommendedLocations
          : selectedAccount.locations,
    );
    const experiment = uniq(
      this.state.showAllLocations.experiment
        ? allLocations
        : selectedAccount.recommendedLocations.length > 0
          ? selectedAccount.recommendedLocations
          : selectedAccount.locations,
    );

    return {
      combinedLocations: {
        control,
        experiment,
      },
      recommendedLocations: selectedAccount.recommendedLocations,
      locations: selectedAccount.locations,
      hasChoices,
    };
  };

  public toggleAllLocations = (type: 'control' | 'experiment'): void => {
    this.state.showAllLocations[type] = !this.state.showAllLocations[type];

    const locationChoices = this.getLocationChoices();

    this.stage.canaryConfig.scopes.forEach(scope => {
      if (!locationChoices.combinedLocations.control.includes(scope.controlLocation)) {
        delete scope.controlLocation;
      }
      if (!locationChoices.combinedLocations.experiment.includes(scope.experimentLocation)) {
        delete scope.experimentLocation;
      }
    });
  };

  private populateScopeNameChoices = (): void => {
    if (!this.selectedCanaryConfigDetails) {
      return;
    }

    const scopeNames = uniq(map(this.selectedCanaryConfigDetails.metrics, metric => metric.scopeName || 'default'));
    this.scopeNames = !isEmpty(scopeNames) ? scopeNames : ['default'];
    this.setShowAdvancedSettings();

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
    if (
      (this.kayentaAccounts.get(KayentaAccountType.ObjectStore) || []).every(
        account => account.name !== this.stage.canaryConfig.storageAccountName,
      )
    ) {
      delete this.stage.canaryConfig.storageAccountName;
    }
    if (
      (this.kayentaAccounts.get(KayentaAccountType.MetricsStore) || []).every(
        account => account.name !== this.stage.canaryConfig.metricsAccountName,
      )
    ) {
      delete this.stage.canaryConfig.metricsAccountName;
    }
  };

  private deleteCanaryConfigIdIfMissing = (): void => {
    if (this.canaryConfigSummaries.every(s => s.id !== this.stage.canaryConfig.canaryConfigId)) {
      delete this.stage.canaryConfig.canaryConfigId;
    }
  };

  public populateScopeWithExpressions = (): void => {
    this.stage.canaryConfig.scopes[0].controlScope =
      "${ #stage('Clone Server Group')['context']['source']['serverGroupName'] }";
    this.stage.canaryConfig.scopes[0].controlLocation = '${ deployedServerGroups[0].region }';
    this.stage.canaryConfig.scopes[0].experimentScope = '${ deployedServerGroups[0].serverGroup }';
    this.stage.canaryConfig.scopes[0].experimentLocation = '${ deployedServerGroups[0].region }';
  };

  public onLifetimeChange = (): void => {
    this.stage.canaryConfig.lifetimeDuration = getDurationString(this.state.lifetime);
  };

  private updateLifetimeFromHoursToDuration = (): void => {
    if (has(this.stage, ['canaryConfig', 'lifetimeHours'])) {
      const hours = parseInt(this.stage.canaryConfig.lifetimeHours, 10);
      if (isFinite(hours)) {
        const fractional = parseFloat(this.stage.canaryConfig.lifetimeHours) - hours;
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

  public getLifetimeClassnames = (): string => {
    if (this.state.lifetimeHoursUpdatedToDuration) {
      return 'alert alert-warning';
    }
    return '';
  };

  public getLifetimeInputClassnames = (): string => {
    const { hours, minutes } = this.state.lifetime;
    if (hours === 0 && minutes === 0) {
      return 'ng-invalid ng-invalid-required';
    }
    return '';
  };

  private loadProviders = async (): Promise<string[]> => {
    this.providers = (await AccountService.listProviders(this.$scope.application)).filter(p =>
      REAL_TIME_AUTOMATIC_PROVIDERS.includes(p),
    );
    // If none of the application's providers support real time automatic mode, don't show it!
    if (!this.providers.length) {
      this.state.analysisTypes = [KayentaAnalysisType.RealTime, KayentaAnalysisType.Retrospective];
    }
    return this.providers;
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
    this.clusterList = AppListExtractor.getClusters(
      [this.$scope.application],
      sg =>
        has(this.stage, 'deployments.baseline.account') ? sg.account === this.stage.deployments.baseline.account : true,
    );

    this.setAtlasEnvironment();
  };

  private setAtlasEnvironment = (): void => {
    if (this.metricStore === 'atlas' && this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
      this.stage.canaryConfig.scopes.forEach(scope => {
        if (this.stage.deployments.baseline.account) {
          const accountDetails = this.accounts.find(({ name }) => this.stage.deployments.baseline.account === name);
          accountDetails && set(scope, 'extendedScopeParams.environment', accountDetails.environment);
        } else {
          unset(scope, 'extendedScopeParams.environment');
        }
      });
    }
  };

  private setLegacySiteLocalRecipients = (): void => {
    const recipients = get(this.stage.canaryConfig, 'siteLocal.notificationEmail');

    if (Array.isArray(recipients)) {
      this.state.legacySiteLocalRecipients = recipients.join(',');
    } else {
      this.state.legacySiteLocalRecipients = isString(recipients) ? recipients : '';
    }
  };

  public handleLegacySiteLocalRecipientsChange = (): void => {
    if (this.state.legacySiteLocalRecipients.includes('${')) {
      set(this.stage.canaryConfig, 'siteLocal.notificationEmail', this.state.legacySiteLocalRecipients);
    } else if (this.state.legacySiteLocalRecipients.length) {
      set(
        this.stage.canaryConfig,
        'siteLocal.notificationEmail',
        this.state.legacySiteLocalRecipients.split(',').map(email => email.trim()),
      );
    } else {
      delete this.stage.canaryConfig.siteLocal;
    }
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
    return NameUtils.getClusterName(serverGroup.application, serverGroup.stack, serverGroup.freeFormDetails);
  };

  public addPair = async (): Promise<void> => {
    this.stage.deployments.serverGroupPairs = this.stage.deployments.serverGroupPairs || [];
    const provider = await (has(this.stage, 'deployments.baseline.cloudProvider')
      ? Promise.resolve(this.stage.deployments.baseline.cloudProvider)
      : Promise.resolve(ProviderSelectionService.selectProvider(this.$scope.application, 'serverGroup')));
    this.stage.deployments.baseline.cloudProvider = provider;
    const config = CloudProviderRegistry.getValue(provider, 'serverGroup');

    const command: any = await this.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(
      provider,
      null,
      null,
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
        useSourceCapacity: true,
      },
      overrides: {
        capacity: {
          min: 1,
          max: 1,
          desired: 1,
        },
        useSourceCapacity: false,
      },
      imageSourceText: `
        <p>This dialogue will configure two server groups.</p>
        <p>
          The <b>Baseline</b> server group's image will be resolved
          from the <b>Newest</b> server group
          in the <b>Baseline Version</b> cluster defined in this stage.
        </p>
        <p>
          The <b>Canary</b> server group's image will be resolved from
          an upstream <b>Bake</b> or <b>Find Image</b> stage.
        </p>`,
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
      const control = this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result);
      const experiment = cloneDeep(control);

      const cleanup = (serverGroup: any, type: string) => {
        delete serverGroup.backingData;
        if (serverGroup.freeFormDetails && serverGroup.freeFormDetails.split('-').pop() === type) {
          return;
        }
        serverGroup.freeFormDetails = `${serverGroup.freeFormDetails ? `${serverGroup.freeFormDetails}-` : ''}${type}`;
      };

      cleanup(control, 'baseline');
      cleanup(experiment, 'canary');
      this.stage.deployments.serverGroupPairs = [{ control, experiment }];
      this.$scope.$applyAsync();
    } catch (e) {
      this.$log.warn('Error creating server group pair for Kayenta stage: ', e);
    }
  };

  public editServerGroup = async (
    serverGroup: any,
    index: number,
    type: keyof IKayentaServerGroupPair,
  ): Promise<void> => {
    serverGroup.provider = serverGroup.provider || serverGroup.cloudProvider;
    const config = CloudProviderRegistry.getValue(serverGroup.cloudProvider, 'serverGroup');
    try {
      const title = `Configure ${type[0].toUpperCase() + type.substring(1)} Server Group`;
      const application = this.$scope.application;

      const command = await this.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline(
        application,
        serverGroup,
        null,
        null,
      );
      command.viewState = {
        ...command.viewState,
        disableStrategySelection: true,
        hideClusterNamePreview: true,
        readOnlyFields: {
          credentials: true,
          region: true,
          subnet: true,
          useSourceCapacity: true,
        },
        imageSourceText: this.resolveImageSourceText(type),
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

      this.stage.deployments.serverGroupPairs[index][
        type
      ] = this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result);
    } catch (e) {
      this.$log.warn('Error editing server group pair for Kayenta stage: ', e);
    }
  };

  public deletePair = (index: number): void => {
    (this.stage.deployments.serverGroupPairs || []).splice(index, 1);
  };

  private resolveImageSourceText(type: keyof IKayentaServerGroupPair): string {
    if (type === 'experiment') {
      return `This server group's image will be resolved from an upstream
        <b>Bake</b> or <b>Find Image</b> stage.`;
    } else {
      return `This server group's image will be resolved from the <b>Newest</b>
        server group in the <b>Baseline Version</b> cluster defined in this stage.`;
    }
  }

  public handleAtlasDatasetChange = (event: Event): void => {
    const { checked } = event.target as HTMLInputElement;
    this.$scope.$applyAsync(() => {
      this.stage.canaryConfig.scopes.forEach(scope => {
        set(scope, 'extendedScopeParams.dataset', checked ? 'global' : 'regional');
        set(
          scope,
          'extendedScopeParams.type',
          this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic ? 'asg' : this.state.atlasScopeType,
        );
      });
    });
  };

  public onAtlasScopeTypeChange = (): void => {
    this.$scope.$applyAsync(() => {
      this.stage.canaryConfig.scopes.forEach(scope => {
        set(scope, 'extendedScopeParams.type', this.state.atlasScopeType);
      });
    });
  };

  public handleExtendedScopeParamsChange = (): void => {
    if (this.metricStore !== 'atlas') {
      return;
    }

    this.stage.canaryConfig.scopes.forEach(scope => {
      set(
        scope,
        'extendedScopeParams.type',
        this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic ? 'asg' : this.state.atlasScopeType,
      );
    });

    if (this.stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
      this.stage.canaryConfig.scopes.forEach(scope => {
        set(scope, 'extendedScopeParams.dataset', this.state.useAtlasGlobalDataset ? 'global' : 'regional');
      });
      this.setAtlasEnvironment();
    }
    this.$scope.$applyAsync();
  };

  public onDelayBeforeCleanupChange = (): void => {
    this.stage.deployments.delayBeforeCleanup = getDurationString(this.state.delayBeforeCleanup);
  };
}
