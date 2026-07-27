import { CanarySettings } from '../../canary.settings';
import type { IKayentaAccount, IKayentaStage } from '../../domain';
import { KayentaAccountType, KayentaAnalysisType } from '../../domain';

import type { DeckRuntimeServices } from '@spinnaker/core';

import type { IKayentaCloneServerGroupModal, IKayentaServerGroupModalDependencies } from './kayentaStageConfig.model';
import {
  addPair,
  createInitialKayentaStageConfigModel,
  editServerGroup,
  handleAnalysisTypeChange,
  handleLegacySiteLocalRecipientsChange,
  initializeKayentaStage,
  populateScopeWithExpressions,
} from './kayentaStageConfig.model';

describe('kayentaStageConfig model', () => {
  let originalStorageAccountName: string;
  let originalMetricsAccountName: string;
  let originalLegacySiteLocalFieldsEnabled: boolean;

  beforeEach(() => {
    originalStorageAccountName = CanarySettings.storageAccountName;
    originalMetricsAccountName = CanarySettings.metricsAccountName;
    originalLegacySiteLocalFieldsEnabled = CanarySettings.legacySiteLocalFieldsEnabled;
    CanarySettings.storageAccountName = 'settings-storage';
    CanarySettings.metricsAccountName = 'settings-metrics';
    CanarySettings.legacySiteLocalFieldsEnabled = true;
  });

  afterEach(() => {
    CanarySettings.storageAccountName = originalStorageAccountName;
    CanarySettings.metricsAccountName = originalMetricsAccountName;
    CanarySettings.legacySiteLocalFieldsEnabled = originalLegacySiteLocalFieldsEnabled;
  });

  it('initializes stage defaults from loaded backing data', () => {
    const stage = createStage();
    const model = createInitialKayentaStageConfigModel();

    initializeKayentaStage(stage, model, {
      kayentaAccounts: createKayentaAccounts({ storage: ['loaded-storage'], metrics: ['loaded-metrics'] }),
      providers: ['aws'],
    });

    expect(stage.canaryConfig.storageAccountName).toBe('loaded-storage');
    expect(stage.canaryConfig.metricsAccountName).toBe('loaded-metrics');
    expect(stage.canaryConfig.scoreThresholds).toEqual({ marginal: null, pass: null });
    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTimeAutomatic);
    expect(stage.canaryConfig.scopes).toEqual([{ scopeName: 'default' }]);
    expect(model.state.analysisTypes).toEqual([
      KayentaAnalysisType.RealTimeAutomatic,
      KayentaAnalysisType.RealTime,
      KayentaAnalysisType.Retrospective,
    ]);
  });

  it('removes configured accounts that are missing from non-empty loaded account lists', () => {
    const stage = createStage({
      canaryConfig: { storageAccountName: 'stale-storage', metricsAccountName: 'stale-metrics' },
    });
    const model = createInitialKayentaStageConfigModel();

    initializeKayentaStage(stage, model, {
      kayentaAccounts: createKayentaAccounts({ storage: ['loaded-storage'], metrics: ['loaded-metrics'] }),
      providers: ['aws'],
    });

    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'storageAccountName')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'metricsAccountName')).toBe(false);
  });

  it('omits realTimeAutomatic when no loaded providers support it', () => {
    const stage = createStage();
    const model = createInitialKayentaStageConfigModel();

    initializeKayentaStage(stage, model, { providers: ['kubernetes'] });

    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTime);
    expect(model.state.analysisTypes).toEqual([KayentaAnalysisType.RealTime, KayentaAnalysisType.Retrospective]);
  });

  it('parses existing durations and migrates legacy lifetimeHours', () => {
    const stage = createStage({
      canaryConfig: { lifetimeDuration: 'PT2H15M', lifetimeHours: '3.5' },
      deployments: { delayBeforeCleanup: 'PT1H30M' },
      isNew: false,
    });
    const model = createInitialKayentaStageConfigModel();

    initializeKayentaStage(stage, model, { providers: ['aws'] });

    expect(model.state.lifetime).toEqual({ hours: 2, minutes: 15 });
    expect(model.state.delayBeforeCleanup).toEqual({ hours: 1, minutes: 30 });
    expect(stage.canaryConfig.lifetimeDuration).toBe('PT3H30M');
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'lifetimeHours')).toBe(false);
    expect(model.state.lifetimeHoursUpdatedToDuration).toBe(true);
  });

  it('removes real-time automatic fields when changing to realTime', () => {
    const stage = createStageWithAnalysisFields();
    const model = createInitialKayentaStageConfigModel();
    model.metricStore = 'atlas';
    model.state.atlasScopeType = 'cluster';

    handleAnalysisTypeChange(stage, model, KayentaAnalysisType.RealTime);

    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTime);
    expect(Object.prototype.hasOwnProperty.call(stage, 'deployments')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'startTimeIso')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'endTimeIso')).toBe(false);
    expect(stage.canaryConfig.scopes[0].controlLocation).toBe('us-east-1');
    expect(stage.canaryConfig.scopes[0].experimentLocation).toBe('us-west-2');
    expect(stage.canaryConfig.scopes[0].extendedScopeParams).toEqual({ type: 'cluster' });
  });

  it('removes manual location and time fields when changing to realTimeAutomatic', () => {
    const stage = createStageWithAnalysisFields();
    const model = createInitialKayentaStageConfigModel();
    model.providers = ['gce'];

    initializeKayentaStage(createStage(), model, { applicationName: 'fnord', providers: ['gce'] });

    handleAnalysisTypeChange(stage, model, KayentaAnalysisType.RealTimeAutomatic);

    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTimeAutomatic);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'startTimeIso')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'endTimeIso')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'controlLocation')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig.scopes[0], 'experimentLocation')).toBe(false);
    expect(stage.deployments).toEqual({
      baseline: { cloudProvider: 'gce', application: 'fnord', cluster: null, account: null },
      delayBeforeCleanup: 'PT0H0M',
      serverGroupPairs: [],
    });
  });

  it('removes real-time fields when changing to retrospective', () => {
    const stage = createStageWithAnalysisFields();
    const model = createInitialKayentaStageConfigModel();
    model.metricStore = 'atlas';
    model.state.atlasScopeType = 'cluster';

    handleAnalysisTypeChange(stage, model, KayentaAnalysisType.Retrospective);

    expect(stage.analysisType).toBe(KayentaAnalysisType.Retrospective);
    expect(Object.prototype.hasOwnProperty.call(stage, 'deployments')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'beginCanaryAnalysisAfterMins')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'lifetimeDuration')).toBe(false);
    expect(stage.canaryConfig.scopes[0].extendedScopeParams).toEqual({ type: 'cluster' });
  });

  it('stores legacy site local recipients as expressions, arrays, or removes empty values', () => {
    const stage = createStage();
    const model = createInitialKayentaStageConfigModel();

    model.state.legacySiteLocalRecipients = '${ parameters.notificationEmail }';
    handleLegacySiteLocalRecipientsChange(stage, model);
    expect(stage.canaryConfig.siteLocal.notificationEmail).toBe('${ parameters.notificationEmail }');

    model.state.legacySiteLocalRecipients = 'one@example.com, two@example.com';
    handleLegacySiteLocalRecipientsChange(stage, model);
    expect(stage.canaryConfig.siteLocal.notificationEmail).toEqual(['one@example.com', 'two@example.com']);

    model.state.legacySiteLocalRecipients = '';
    handleLegacySiteLocalRecipientsChange(stage, model);
    expect(Object.prototype.hasOwnProperty.call(stage.canaryConfig, 'siteLocal')).toBe(false);
  });

  it('populates the first scope with the existing SpEL defaults', () => {
    const stage = createStage({ canaryConfig: { scopes: [{ scopeName: 'default', extendedScopeParams: {} }] } });

    populateScopeWithExpressions(stage);

    expect(stage.canaryConfig.scopes[0]).toEqual(
      jasmine.objectContaining({
        controlScope: "${ #stage('Clone Server Group')['context']['source']['serverGroupName'] }",
        controlLocation: '${ deployedServerGroups[0].region }',
        experimentScope: '${ deployedServerGroups[0].serverGroup }',
        experimentLocation: '${ deployedServerGroups[0].region }',
      }),
    );
  });

  it('selects only providers with React clone server group modals when adding a pair', async () => {
    const stage = createStage({ deployments: { baseline: {}, serverGroupPairs: [] } });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce' });

    await addPair(stage, deps);

    expect(deps.providerSelectionService.selectProvider).toHaveBeenCalledWith(
      deps.application,
      'serverGroup',
      jasmine.any(Function),
    );
    const filterFn = (deps.providerSelectionService.selectProvider as jasmine.Spy).calls.argsFor(0)[2];
    expect(filterFn(null, null, { serverGroup: { CloneServerGroupModal: {} } })).toBe(true);
    expect(filterFn(null, null, { serverGroup: {} })).toBe(false);
    expect(stage.deployments.baseline.cloudProvider).toBe('gce');
    const cloneServerGroupModal = getCloneServerGroupModal(deps);
    expect(cloneServerGroupModal.show).toHaveBeenCalledWith(
      jasmine.objectContaining({
        application: deps.application,
        command: jasmine.any(Object),
        title: 'Add Baseline + Canary Pair',
      }),
      deps.runtimeServices,
    );
    expect((cloneServerGroupModal.show as jasmine.Spy).calls.argsFor(0)[1]).toBe(deps.runtimeServices);
  });

  it('passes the same runtime services to the React clone modal when editing a pair', async () => {
    const control = { application: 'fnord', cloudProvider: 'gce', freeFormDetails: 'baseline' };
    const stage = createStage({
      deployments: { baseline: { cloudProvider: 'gce' }, serverGroupPairs: [{ control, experiment: {} }] },
    });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce' });

    await editServerGroup(stage, deps, control, 0, 'control');

    const cloneServerGroupModal = getCloneServerGroupModal(deps);
    expect(cloneServerGroupModal.show).toHaveBeenCalledWith(
      jasmine.objectContaining({
        application: deps.application,
        command: jasmine.any(Object),
        title: 'Configure Control Server Group',
      }),
      deps.runtimeServices,
    );
    expect((cloneServerGroupModal.show as jasmine.Spy).calls.argsFor(0)[1]).toBe(deps.runtimeServices);
    expect(stage.deployments.serverGroupPairs[0].control).toEqual({ application: 'fnord', freeFormDetails: '' });
  });

  it('rejects clearly when an existing baseline provider has no React clone server group modal', async () => {
    const stage = createStage({ deployments: { baseline: { cloudProvider: 'oracle' }, serverGroupPairs: [] } });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce', registeredModal: null });

    await expectAsync(addPair(stage, deps)).toBeRejectedWithError(
      'No React clone server group modal is registered for provider "oracle".',
    );
    expect(deps.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline).not.toHaveBeenCalled();
  });
});

function createStage(
  overrides: Partial<IKayentaStage> & { canaryConfig?: any; deployments?: any } = {},
): IKayentaStage {
  return {
    analysisType: undefined,
    canaryConfig: {
      canaryAnalysisIntervalMins: '5',
      canaryConfigId: 'config-id',
      combinedCanaryResultStrategy: 'LOWEST',
      scopes: [],
      ...overrides.canaryConfig,
    },
    deployments: overrides.deployments,
    isNew: overrides.isNew === undefined ? true : overrides.isNew,
    ...overrides,
  } as IKayentaStage;
}

function createStageWithAnalysisFields(): IKayentaStage {
  return createStage({
    analysisType: KayentaAnalysisType.RealTimeAutomatic,
    canaryConfig: {
      beginCanaryAnalysisAfterMins: '10',
      lifetimeDuration: 'PT1H0M',
      scopes: [
        {
          scopeName: 'default',
          controlLocation: 'us-east-1',
          experimentLocation: 'us-west-2',
          startTimeIso: '2026-05-21T10:00:00Z',
          endTimeIso: '2026-05-21T11:00:00Z',
          extendedScopeParams: { dataset: 'regional', environment: 'test', type: 'asg' },
        },
      ],
    },
    deployments: { baseline: { cloudProvider: 'aws' }, serverGroupPairs: [], delayBeforeCleanup: 'PT0H0M' },
  });
}

function createServerGroupModalDependencies({
  selectedProvider,
  registeredModal = {
    show: jasmine.createSpy('show').and.callFake((props) => Promise.resolve(props.command)),
  },
}: {
  selectedProvider: string;
  registeredModal?: IKayentaCloneServerGroupModal | null;
}): IKayentaServerGroupModalDependencies {
  const command = { viewState: {}, strategy: 'redblack' };
  const runtimeServices = {} as DeckRuntimeServices;
  return {
    application: { name: 'fnord' },
    cloudProviderRegistry: {
      getValue: jasmine.createSpy('getValue').and.callFake(() => ({ CloneServerGroupModal: registeredModal })),
    },
    providerSelectionService: {
      selectProvider: jasmine.createSpy('selectProvider').and.resolveTo(selectedProvider),
    },
    serverGroupCommandBuilder: {
      buildNewServerGroupCommandForPipeline: jasmine
        .createSpy('buildNewServerGroupCommandForPipeline')
        .and.resolveTo(command),
      buildServerGroupCommandFromPipeline: jasmine
        .createSpy('buildServerGroupCommandFromPipeline')
        .and.resolveTo(command),
    },
    serverGroupTransformer: {
      convertServerGroupCommandToDeployConfiguration: jasmine
        .createSpy('convertServerGroupCommandToDeployConfiguration')
        .and.returnValue({ application: 'fnord', freeFormDetails: '' }),
    },
    runtimeServices,
  };
}

function getCloneServerGroupModal(deps: IKayentaServerGroupModalDependencies): IKayentaCloneServerGroupModal {
  const modal = deps.cloudProviderRegistry.getValue('gce', 'serverGroup').CloneServerGroupModal;
  if (!modal) {
    throw new Error('Expected a registered clone server group modal.');
  }
  return modal;
}

function createKayentaAccounts({ storage, metrics }: { storage: string[]; metrics: string[] }) {
  const accounts = new Map<KayentaAccountType, IKayentaAccount[]>();
  accounts.set(
    KayentaAccountType.ObjectStore,
    storage.map((name) => ({ name, type: 'object', supportedTypes: [KayentaAccountType.ObjectStore] })),
  );
  accounts.set(
    KayentaAccountType.MetricsStore,
    metrics.map((name) => ({
      name,
      type: 'metrics',
      supportedTypes: [KayentaAccountType.MetricsStore],
      locations: [],
      recommendedLocations: [],
    })),
  );
  return accounts;
}
