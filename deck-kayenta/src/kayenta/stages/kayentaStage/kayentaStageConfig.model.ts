import { CanarySettings } from 'kayenta/canary.settings';
import type {
  ICanaryConfig,
  ICanaryConfigSummary,
  IKayentaAccount,
  IKayentaServerGroupPair,
  IKayentaStage,
  IKayentaStageCanaryConfigScope,
} from 'kayenta/domain';
import { KayentaAccountType, KayentaAnalysisType } from 'kayenta/domain';
import type { IDuration } from 'kayenta/utils/duration';
import { getDurationString, parseDurationString } from 'kayenta/utils/duration';
import { cloneDeep, first, get, has, isEmpty, isFinite, isString, map, set, uniq, unset } from 'lodash';

import type { IAccountDetails } from '@spinnaker/core';
import { NameUtils } from '@spinnaker/core';

export const REAL_TIME_AUTOMATIC_PROVIDERS = ['gce', 'aws', 'titus'];

export interface IKayentaStageConfigViewState {
  useLookback: boolean;
  backingDataLoading: boolean;
  lifetimeHoursUpdatedToDuration: boolean;
  lifetime: IDuration;
  showAdvancedSettings: boolean;
  useAtlasGlobalDataset: boolean;
  atlasScopeType: string;
  legacySiteLocalRecipients: string;
  showLegacySiteLocalRecipients: boolean;
  showAllLocations: { control: boolean; experiment: boolean };
  delayBeforeCleanup: IDuration;
  analysisTypes: KayentaAnalysisType[];
}

export interface IKayentaStageConfigModel {
  state: IKayentaStageConfigViewState;
  canaryConfigSummaries: ICanaryConfigSummary[];
  selectedCanaryConfigDetails: ICanaryConfig;
  scopeNames: string[];
  kayentaAccounts: Map<KayentaAccountType, IKayentaAccount[]>;
  metricStore: string;
  providers: string[];
  accounts: IAccountDetails[];
  clusterList: string[];
  applicationName: string;
}

export interface IKayentaStageBackingData {
  selectedCanaryConfigDetails?: ICanaryConfig;
  kayentaAccounts?: Map<KayentaAccountType, IKayentaAccount[]>;
  providers?: string[];
  accounts?: IAccountDetails[];
  canaryConfigSummaries?: ICanaryConfigSummary[];
  applicationName?: string;
  clusterList?: string[];
}

export interface IKayentaLocationChoices {
  combinedLocations: { control: string[]; experiment: string[] };
  recommendedLocations: string[];
  locations: string[];
  hasChoices: boolean;
}

export interface IKayentaServerGroupModalDependencies {
  application: any;
  cloudProviderRegistry: { getValue(provider: string, category: string): any };
  providerSelectionService: { selectProvider(application: any, category: string): Promise<string> | string };
  serverGroupCommandBuilder: {
    buildNewServerGroupCommandForPipeline(provider: string, cluster: any, credentials: any): Promise<any>;
    buildServerGroupCommandFromPipeline(
      application: any,
      serverGroup: any,
      cluster: any,
      credentials: any,
    ): Promise<any>;
  };
  serverGroupTransformer: { convertServerGroupCommandToDeployConfiguration(command: any): any };
  $uibModal: { open(options: any): { result: Promise<any> } };
}

export function createInitialKayentaStageConfigModel(): IKayentaStageConfigModel {
  return {
    state: {
      useLookback: false,
      backingDataLoading: false,
      lifetimeHoursUpdatedToDuration: false,
      lifetime: { hours: 0, minutes: 0 },
      showAdvancedSettings: false,
      useAtlasGlobalDataset: false,
      atlasScopeType: 'cluster',
      legacySiteLocalRecipients: '',
      showLegacySiteLocalRecipients: CanarySettings.legacySiteLocalFieldsEnabled,
      showAllLocations: { control: false, experiment: false },
      delayBeforeCleanup: { hours: 0, minutes: 0 },
      analysisTypes: [
        KayentaAnalysisType.RealTimeAutomatic,
        KayentaAnalysisType.RealTime,
        KayentaAnalysisType.Retrospective,
      ],
    },
    canaryConfigSummaries: [],
    selectedCanaryConfigDetails: null,
    scopeNames: [],
    kayentaAccounts: new Map<KayentaAccountType, IKayentaAccount[]>(),
    metricStore: null,
    providers: [],
    accounts: null,
    clusterList: [],
    applicationName: null,
  };
}

export function initializeKayentaStage(
  stage: IKayentaStage,
  model: IKayentaStageConfigModel,
  backingData: IKayentaStageBackingData,
): void {
  model.selectedCanaryConfigDetails = backingData.selectedCanaryConfigDetails || null;
  model.kayentaAccounts = backingData.kayentaAccounts || new Map<KayentaAccountType, IKayentaAccount[]>();
  model.providers = (backingData.providers || []).filter((p) => REAL_TIME_AUTOMATIC_PROVIDERS.includes(p));
  model.accounts = backingData.accounts || null;
  model.canaryConfigSummaries = backingData.canaryConfigSummaries || [];
  model.clusterList = backingData.clusterList || [];
  model.applicationName = backingData.applicationName || null;

  if (!model.providers.length) {
    model.state.analysisTypes = [KayentaAnalysisType.RealTime, KayentaAnalysisType.Retrospective];
  }

  const firstStorageAccount = first(model.kayentaAccounts.get(KayentaAccountType.ObjectStore));
  const firstMetricsAccount = first(model.kayentaAccounts.get(KayentaAccountType.MetricsStore));

  stage.canaryConfig = {
    storageAccountName: (firstStorageAccount && firstStorageAccount.name) || CanarySettings.storageAccountName,
    metricsAccountName: (firstMetricsAccount && firstMetricsAccount.name) || CanarySettings.metricsAccountName,
    ...stage.canaryConfig,
    scoreThresholds: {
      marginal: null,
      pass: null,
      ...get(stage, 'canaryConfig.scoreThresholds'),
    },
  };

  stage.analysisType = stage.analysisType || model.state.analysisTypes[0];

  if (!stage.canaryConfig.scopes || !stage.canaryConfig.scopes.length) {
    stage.canaryConfig.scopes = [{ scopeName: 'default' } as IKayentaStageCanaryConfigScope];
  }

  if (!stage.isNew) {
    model.state.lifetime = parseDurationString(get(stage, 'canaryConfig.lifetimeDuration'));
    model.state.delayBeforeCleanup = parseDurationString(get(stage, 'deployments.delayBeforeCleanup'));
  }

  if (stage.canaryConfig.lookbackMins) {
    model.state.useLookback = true;
  }

  populateScopeNameChoices(stage, model);
  setMetricStore(stage, model);
  setLocations(stage, model);
  setShowAdvancedSettings(model);
  deleteConfigAccountsIfMissing(stage, model);
  updateLifetimeFromHoursToDuration(stage, model);
  deleteCanaryConfigIdIfMissing(stage, model);

  if (CanarySettings.legacySiteLocalFieldsEnabled) {
    setLegacySiteLocalRecipients(stage, model);
  }

  if (stage.isNew && stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
    initializeRealTimeAutomaticAnalysisType(stage, model);
  }
}

export function handleAnalysisTypeChange(
  stage: IKayentaStage,
  model: IKayentaStageConfigModel,
  type: KayentaAnalysisType,
): void {
  stage.analysisType = type;

  switch (stage.analysisType) {
    case KayentaAnalysisType.RealTime:
      delete stage.canaryConfig.scopes[0].startTimeIso;
      delete stage.canaryConfig.scopes[0].endTimeIso;
      delete stage.deployments;
      clearAtlasDatasetAndEnvironment(stage, model);
      break;
    case KayentaAnalysisType.RealTimeAutomatic:
      delete stage.canaryConfig.scopes[0].startTimeIso;
      delete stage.canaryConfig.scopes[0].endTimeIso;
      delete stage.canaryConfig.scopes[0].controlLocation;
      delete stage.canaryConfig.scopes[0].experimentLocation;
      initializeRealTimeAutomaticAnalysisType(stage, model);
      break;
    case KayentaAnalysisType.Retrospective:
      delete stage.canaryConfig.beginCanaryAnalysisAfterMins;
      delete stage.canaryConfig.lifetimeDuration;
      delete stage.deployments;
      clearAtlasDatasetAndEnvironment(stage, model);
      break;
  }
}

export function populateScopeWithExpressions(stage: IKayentaStage): void {
  stage.canaryConfig.scopes[0].controlScope =
    "${ #stage('Clone Server Group')['context']['source']['serverGroupName'] }";
  stage.canaryConfig.scopes[0].controlLocation = '${ deployedServerGroups[0].region }';
  stage.canaryConfig.scopes[0].experimentScope = '${ deployedServerGroups[0].serverGroup }';
  stage.canaryConfig.scopes[0].experimentLocation = '${ deployedServerGroups[0].region }';
}

export function handleLegacySiteLocalRecipientsChange(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  if (model.state.legacySiteLocalRecipients.includes('${')) {
    set(stage.canaryConfig, 'siteLocal.notificationEmail', model.state.legacySiteLocalRecipients);
  } else if (model.state.legacySiteLocalRecipients.length) {
    set(
      stage.canaryConfig,
      'siteLocal.notificationEmail',
      model.state.legacySiteLocalRecipients.split(',').map((email) => email.trim()),
    );
  } else {
    delete stage.canaryConfig.siteLocal;
  }
}

export function getLocationChoices(stage: IKayentaStage, model: IKayentaStageConfigModel): IKayentaLocationChoices {
  const accounts = model.kayentaAccounts.get(KayentaAccountType.MetricsStore);
  const selectedAccount = accounts && accounts.find(({ name }) => name === stage.canaryConfig.metricsAccountName);
  if (!selectedAccount) {
    return {
      combinedLocations: { control: [], experiment: [] },
      recommendedLocations: [],
      locations: [],
      hasChoices: false,
    };
  }

  const recommendedLocations = selectedAccount.recommendedLocations || [];
  const locations = selectedAccount.locations || [];
  const hasChoices = locations.length > 0 || recommendedLocations.length > 0;
  const allLocations = recommendedLocations.concat(locations);
  const control = uniq(
    model.state.showAllLocations.control
      ? allLocations
      : recommendedLocations.length
      ? recommendedLocations
      : locations,
  );
  const experiment = uniq(
    model.state.showAllLocations.experiment
      ? allLocations
      : recommendedLocations.length
      ? recommendedLocations
      : locations,
  );

  return { combinedLocations: { control, experiment }, recommendedLocations, locations, hasChoices };
}

export function onLifetimeChange(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  stage.canaryConfig.lifetimeDuration = getDurationString(model.state.lifetime);
}

export function onDelayBeforeCleanupChange(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  stage.deployments.delayBeforeCleanup = getDurationString(model.state.delayBeforeCleanup);
}

export function getRegion(serverGroup: any): string {
  if (serverGroup.region) {
    return serverGroup.region;
  }
  const availabilityZones = serverGroup.availabilityZones;
  return availabilityZones
    ? Object.keys(availabilityZones).length
      ? Object.keys(availabilityZones)[0]
      : 'n/a'
    : 'n/a';
}

export function getServerGroupName(serverGroup: any): string {
  return NameUtils.getClusterName(serverGroup.application, serverGroup.stack, serverGroup.freeFormDetails);
}

export async function addPair(stage: IKayentaStage, deps: IKayentaServerGroupModalDependencies): Promise<void> {
  stage.deployments.serverGroupPairs = stage.deployments.serverGroupPairs || [];
  const provider = has(stage, 'deployments.baseline.cloudProvider')
    ? stage.deployments.baseline.cloudProvider
    : await deps.providerSelectionService.selectProvider(deps.application, 'serverGroup');
  stage.deployments.baseline.cloudProvider = provider;
  const config = deps.cloudProviderRegistry.getValue(provider, 'serverGroup');

  const command = await deps.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(provider, null, null);
  command.viewState = {
    ...command.viewState,
    requiresTemplateSelection: true,
    disableStrategySelection: true,
    hideClusterNamePreview: true,
    readOnlyFields: { credentials: true, region: true, subnet: true, useSourceCapacity: true },
    overrides: { capacity: { min: 1, max: 1, desired: 1 }, useSourceCapacity: false },
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

  const result = await showServerGroupModal(config, deps, 'Add Baseline + Canary Pair', command);
  const control = deps.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result);
  const experiment = cloneDeep(control);

  cleanupServerGroup(control, 'baseline');
  cleanupServerGroup(experiment, 'canary');
  stage.deployments.serverGroupPairs = [{ control, experiment }];
}

export async function editServerGroup(
  stage: IKayentaStage,
  deps: IKayentaServerGroupModalDependencies,
  serverGroup: any,
  index: number,
  type: keyof IKayentaServerGroupPair,
): Promise<void> {
  serverGroup.provider = serverGroup.provider || serverGroup.cloudProvider;
  const config = deps.cloudProviderRegistry.getValue(serverGroup.cloudProvider, 'serverGroup');
  const title = `Configure ${type[0].toUpperCase() + type.substring(1)} Server Group`;
  const command = await deps.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline(
    deps.application,
    serverGroup,
    null,
    null,
  );
  command.viewState = {
    ...command.viewState,
    disableStrategySelection: true,
    hideClusterNamePreview: true,
    readOnlyFields: { credentials: true, region: true, subnet: true, useSourceCapacity: true },
    imageSourceText: resolveImageSourceText(type),
  };
  delete command.strategy;

  const result = await showServerGroupModal(config, deps, title, command);
  stage.deployments.serverGroupPairs[index][
    type
  ] = deps.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(result);
}

function initializeRealTimeAutomaticAnalysisType(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  stage.deployments = {
    ...stage.deployments,
    baseline: {
      ...get(stage, 'deployments.baseline'),
      cloudProvider: model.providers[0],
      application: model.applicationName,
      cluster: null,
      account: null,
    },
    serverGroupPairs: [],
  };
  setMetricStore(stage, model);
}

export function setMetricStore(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  model.metricStore = get(model.selectedCanaryConfigDetails, 'metrics[0].query.type');
  if (model.metricStore === 'atlas') {
    setAtlasScopeParams(stage, model);
  } else {
    stage.canaryConfig.scopes.forEach((scope) => unset(scope, 'extendedScopeParams.type'));
  }
}

function setAtlasScopeParams(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  const isRealTimeAutomatic = stage.analysisType === KayentaAnalysisType.RealTimeAutomatic;
  const existingScopeType = get(stage.canaryConfig.scopes[0], 'extendedScopeParams.type') as string;
  if (!isRealTimeAutomatic) {
    model.state.atlasScopeType = existingScopeType !== 'asg' ? existingScopeType : 'cluster';
  }
  stage.canaryConfig.scopes.forEach((scope) => {
    set(scope, 'extendedScopeParams.type', isRealTimeAutomatic ? 'asg' : model.state.atlasScopeType);
  });

  if (isRealTimeAutomatic) {
    model.state.useAtlasGlobalDataset = get(stage.canaryConfig.scopes[0], 'extendedScopeParams.dataset') === 'global';
    stage.canaryConfig.scopes.forEach((scope) => {
      set(scope, 'extendedScopeParams.dataset', model.state.useAtlasGlobalDataset ? 'global' : 'regional');
    });
  }
}

function setLocations(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  const { recommendedLocations, locations, hasChoices } = getLocationChoices(stage, model);
  if (!hasChoices) {
    return;
  }

  model.state.showAllLocations.control = model.state.showAllLocations.experiment =
    recommendedLocations.length === 0 && locations.length > 0;

  stage.canaryConfig.scopes.forEach((scope) => {
    if (!recommendedLocations.includes(scope.controlLocation) && !locations.includes(scope.controlLocation)) {
      delete scope.controlLocation;
    } else if (!recommendedLocations.includes(scope.controlLocation)) {
      model.state.showAllLocations.control = true;
    }

    if (!recommendedLocations.includes(scope.experimentLocation) && !locations.includes(scope.experimentLocation)) {
      delete scope.experimentLocation;
    } else if (!recommendedLocations.includes(scope.experimentLocation)) {
      model.state.showAllLocations.experiment = true;
    }
  });
}

export function populateScopeNameChoices(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  if (!model.selectedCanaryConfigDetails) {
    return;
  }

  const scopeNames = uniq(map(model.selectedCanaryConfigDetails.metrics, (metric) => metric.scopeName || 'default'));
  model.scopeNames = !isEmpty(scopeNames) ? scopeNames : ['default'];
  setShowAdvancedSettings(model);

  if (!isEmpty(stage.canaryConfig.scopes) && !scopeNames.includes(stage.canaryConfig.scopes[0].scopeName)) {
    delete stage.canaryConfig.scopes[0].scopeName;
  } else if (isEmpty(stage.canaryConfig.scopes)) {
    stage.canaryConfig.scopes = [{ scopeName: scopeNames[0] }] as IKayentaStageCanaryConfigScope[];
  }
}

function setShowAdvancedSettings(model: IKayentaStageConfigModel): void {
  model.state.showAdvancedSettings =
    (model.kayentaAccounts.get(KayentaAccountType.MetricsStore) || []).length > 1 ||
    (model.kayentaAccounts.get(KayentaAccountType.ObjectStore) || []).length > 1 ||
    model.scopeNames.length > 1 ||
    CanarySettings.legacySiteLocalFieldsEnabled;
}

function deleteConfigAccountsIfMissing(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  const objectStoreAccounts = model.kayentaAccounts.get(KayentaAccountType.ObjectStore) || [];
  const metricsStoreAccounts = model.kayentaAccounts.get(KayentaAccountType.MetricsStore) || [];

  if (objectStoreAccounts.every((a) => a.name !== stage.canaryConfig.storageAccountName)) {
    delete stage.canaryConfig.storageAccountName;
  }
  if (metricsStoreAccounts.every((a) => a.name !== stage.canaryConfig.metricsAccountName)) {
    delete stage.canaryConfig.metricsAccountName;
  }
}

function deleteCanaryConfigIdIfMissing(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  if (model.canaryConfigSummaries.every((s) => s.id !== stage.canaryConfig.canaryConfigId)) {
    delete stage.canaryConfig.canaryConfigId;
  }
}

function updateLifetimeFromHoursToDuration(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  if (has(stage, ['canaryConfig', 'lifetimeHours'])) {
    const hours = parseInt(stage.canaryConfig.lifetimeHours, 10);
    if (isFinite(hours)) {
      const fractional = parseFloat(stage.canaryConfig.lifetimeHours) - hours;
      const minutes = Math.floor(fractional * 60);
      stage.canaryConfig.lifetimeDuration = `PT${hours}H`;
      if (isFinite(minutes)) {
        stage.canaryConfig.lifetimeDuration += `${minutes}M`;
      }
      model.state.lifetimeHoursUpdatedToDuration = true;
    }
    delete stage.canaryConfig.lifetimeHours;
  }
}

function setLegacySiteLocalRecipients(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  const recipients = get(stage.canaryConfig, 'siteLocal.notificationEmail');
  model.state.legacySiteLocalRecipients = Array.isArray(recipients)
    ? recipients.join(',')
    : isString(recipients)
    ? recipients
    : '';
}

function clearAtlasDatasetAndEnvironment(stage: IKayentaStage, model: IKayentaStageConfigModel): void {
  if (model.metricStore === 'atlas') {
    stage.canaryConfig.scopes.forEach((scope) => {
      unset(scope, 'extendedScopeParams.dataset');
      unset(scope, 'extendedScopeParams.environment');
    });
    setAtlasScopeParams(stage, model);
  }
}

function cleanupServerGroup(serverGroup: any, type: string): void {
  delete serverGroup.backingData;
  if (serverGroup.freeFormDetails && serverGroup.freeFormDetails.split('-').pop() === type) {
    return;
  }
  serverGroup.freeFormDetails = `${serverGroup.freeFormDetails ? `${serverGroup.freeFormDetails}-` : ''}${type}`;
}

function resolveImageSourceText(type: keyof IKayentaServerGroupPair): string {
  if (type === 'experiment') {
    return `This server group's image will be resolved from an upstream
        <b>Bake</b> or <b>Find Image</b> stage.`;
  }
  return `This server group's image will be resolved from the <b>Newest</b>
        server group in the <b>Baseline Version</b> cluster defined in this stage.`;
}

function showServerGroupModal(
  config: any,
  deps: IKayentaServerGroupModalDependencies,
  title: string,
  command: any,
): Promise<any> {
  if (config.CloneServerGroupModal) {
    return config.CloneServerGroupModal.show({ title, application: deps.application, command });
  }
  return deps.$uibModal.open({
    templateUrl: config.cloneServerGroupTemplateUrl,
    controller: `${config.cloneServerGroupController} as ctrl`,
    size: 'lg',
    resolve: { title: () => title, application: () => deps.application, serverGroupCommand: () => command },
  }).result;
}
