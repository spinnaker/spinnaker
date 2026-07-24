import { UIRouterReact } from '@uirouter/react';

import type { IHttpClientImplementation } from '../api';
import { RequestBuilder } from '../api';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { INotificationSettings } from '../config';
import { SETTINGS } from '../config/settings';
import { NotificationService } from '../notification/NotificationService';
import { ScriptStageConfig } from '../pipeline/config/stages/script/ScriptStageConfig';
import { Registry } from '../registry';
import type { IFilterType } from '../search/widgets/SearchFilterTypeRegistry';
import { SearchFilterTypeRegistry } from '../search/widgets/SearchFilterTypeRegistry';

import {
  disposeRuntimeMetadata,
  initializeDynamicRuntimeMetadata,
  initializeRuntimeMetadata,
  registerRuntimeDataSources,
  resetDynamicRuntimeMetadataForTests,
} from './runtimeInitializers';
import type { DeckRuntime } from './DeckRuntime';
import { createDeckRuntime } from './DeckRuntime';

function replaceSearchFilterTypes(filterTypes: IFilterType[]): void {
  (SearchFilterTypeRegistry as any).registry.clear();
  filterTypes.forEach((filterType) => SearchFilterTypeRegistry.register(filterType));
}

describe('runtime initializers', () => {
  const originalFeatureSettings = SETTINGS.feature;
  const originalHiddenStages = SETTINGS.hiddenStages;
  const originalNotifications = SETTINGS.notifications;
  let originalDataSources: ReturnType<typeof ApplicationDataSourceRegistry.getDataSources>;
  let originalHttpClient: IHttpClientImplementation;
  let originalPipeline: typeof Registry.pipeline;
  let originalSearchFilterTypes: IFilterType[];
  let originalUrlBuilder: typeof Registry.urlBuilder;
  let metadataGet: jasmine.Spy;
  let runtime: DeckRuntime;
  const enabledKeys = [
    'serverGroups',
    'loadBalancers',
    'securityGroups',
    'functions',
    'entityTags',
    'executions',
    'pipelineConfigs',
    'runningExecutions',
    'tasks',
    'runningTasks',
  ];

  beforeEach(() => {
    resetDynamicRuntimeMetadataForTests();
    originalDataSources = ApplicationDataSourceRegistry.getDataSources();
    originalHttpClient = RequestBuilder.defaultHttpClient;
    originalPipeline = Registry.pipeline;
    originalUrlBuilder = Registry.urlBuilder;
    originalSearchFilterTypes = SearchFilterTypeRegistry.getRegisteredFilterKeys().map((key) =>
      SearchFilterTypeRegistry.getFilterType(key),
    );
    ApplicationDataSourceRegistry.clearDataSources();
    Registry.reinitialize();
    replaceSearchFilterTypes([SearchFilterTypeRegistry.KEYWORD_FILTER, SearchFilterTypeRegistry.NAME_FILTER]);
    SETTINGS.feature = {
      ...originalFeatureSettings,
      entityTags: true,
      functions: true,
      pipelines: true,
    };
    SETTINGS.hiddenStages = [];
    (SETTINGS as any).notifications = undefined;
    metadataGet = jasmine.createSpy('metadataGet').and.returnValue(Promise.resolve([]));
    RequestBuilder.defaultHttpClient = { get: metadataGet } as IHttpClientImplementation;
    runtime = createDeckRuntime(new UIRouterReact());
  });

  afterEach(() => {
    resetDynamicRuntimeMetadataForTests();
    disposeRuntimeMetadata();
    runtime.dispose();
    ApplicationDataSourceRegistry.clearDataSources();
    originalDataSources.forEach((dataSource) => ApplicationDataSourceRegistry.registerDataSource(dataSource));
    Registry.pipeline = originalPipeline;
    Registry.urlBuilder = originalUrlBuilder;
    replaceSearchFilterTypes(originalSearchFilterTypes);
    SETTINGS.feature = originalFeatureSettings;
    SETTINGS.hiddenStages = originalHiddenStages;
    SETTINGS.notifications = originalNotifications;
    RequestBuilder.defaultHttpClient = originalHttpClient;
  });

  it('registers each enabled data source once in stable registration order', () => {
    const registerDataSource = spyOn(ApplicationDataSourceRegistry, 'registerDataSource').and.callThrough();

    registerRuntimeDataSources(runtime);
    registerRuntimeDataSources(runtime);

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    enabledKeys.forEach((key) => expect(keys.filter((registeredKey) => registeredKey === key).length).toBe(1));
    expect(registerDataSource.calls.allArgs().map(([config]) => config.key)).toEqual(enabledKeys);
  });

  it('omits gated data sources while retaining standard data sources', () => {
    SETTINGS.feature = { ...SETTINGS.feature, entityTags: false, functions: false };

    registerRuntimeDataSources(runtime);

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    expect(keys).not.toContain('functions');
    expect(keys).not.toContain('entityTags');
    expect(keys.slice().sort()).toEqual(enabledKeys.filter((key) => !['functions', 'entityTags'].includes(key)).sort());
  });

  it('registers synchronous runtime metadata exactly once without loading dynamic metadata', () => {
    const getNotificationTypeMetadata = spyOn(NotificationService, 'getNotificationTypeMetadata');

    initializeRuntimeMetadata(runtime);
    initializeRuntimeMetadata(runtime);

    const dataSourceKeys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    enabledKeys.forEach((key) =>
      expect(dataSourceKeys.filter((registeredKey) => registeredKey === key).length).toBe(1),
    );

    const scriptStages = Registry.pipeline.getStageTypes().filter(({ key }) => key === 'script');
    expect(scriptStages.length).toBe(1);
    expect(scriptStages[0].component).toBe(ScriptStageConfig);

    const expectedNotificationKeys = [
      'email',
      'githubStatus',
      'googlechat',
      'microsoftteams',
      'pubsub',
      'slack',
      'sms',
      'cdevents',
    ];
    const notificationKeys = Registry.pipeline.getNotificationTypes().map(({ key }) => key);
    expect(notificationKeys).toEqual(expectedNotificationKeys);
    expectedNotificationKeys.forEach((key) =>
      expect(notificationKeys.filter((registeredKey) => registeredKey === key).length).toBe(1),
    );

    expect(SearchFilterTypeRegistry.getFilterType('account')).toEqual({ key: 'account', name: 'Account' });
    expect(SearchFilterTypeRegistry.getFilterType('region')).toEqual({ key: 'region', name: 'Region' });
    expect(SearchFilterTypeRegistry.getFilterType('stack')).toEqual({ key: 'stack', name: 'Stack' });
    ['account', 'region', 'stack'].forEach((key) =>
      expect(
        SearchFilterTypeRegistry.getRegisteredFilterKeys().filter((registeredKey) => registeredKey === key).length,
      ).toBe(1),
    );
    expect(getNotificationTypeMetadata).not.toHaveBeenCalled();
  });

  it('replaces runtime-bound metadata when a new runtime takes ownership', () => {
    initializeRuntimeMetadata(runtime);
    const firstServerGroups = ApplicationDataSourceRegistry.getDataSources().find(({ key }) => key === 'serverGroups');
    const firstDeployStage = Registry.pipeline.getStageTypes().find(({ key }) => key === 'deploy');
    const replacementRuntime = createDeckRuntime(new UIRouterReact());

    initializeRuntimeMetadata(replacementRuntime);

    const secondServerGroups = ApplicationDataSourceRegistry.getDataSources().find(({ key }) => key === 'serverGroups');
    const deployStages = Registry.pipeline.getStageTypes().filter(({ key }) => key === 'deploy');
    expect(secondServerGroups).not.toBe(firstServerGroups);
    expect(deployStages.length).toBe(1);
    expect(deployStages[0]).not.toBe(firstDeployStage);
    disposeRuntimeMetadata(replacementRuntime);
    replacementRuntime.dispose();
  });

  it('registers supported dynamic metadata exactly once', async () => {
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters: [] },
        { notificationType: 'runtime-custom', uiType: 'CUSTOM', parameters: [] },
      ]),
    );
    metadataGet.and.callFake(({ url }) => {
      if (url.endsWith('/jobs/preconfigured')) {
        return Promise.resolve([
          { type: 'runtime-job', uiType: 'BASIC', label: 'Runtime job', producesArtifacts: false },
        ]);
      }
      if (url.endsWith('/webhooks/preconfigured')) {
        return Promise.resolve([{ type: 'runtime-webhook', label: 'Runtime webhook' }]);
      }
      return Promise.resolve([]);
    });

    await initializeDynamicRuntimeMetadata();
    await initializeDynamicRuntimeMetadata();

    const notificationTypes = Registry.pipeline.getNotificationTypes();
    expect(notificationTypes.filter(({ key }) => key === 'runtime-basic').length).toBe(1);
    expect(notificationTypes.find(({ key }) => key === 'runtime-custom')).toBeUndefined();
    const stageTypes = Registry.pipeline.getStageTypes();
    expect(stageTypes.filter(({ key }) => key === 'runtime-job').length).toBe(1);
    expect(stageTypes.filter(({ key }) => key === 'runtime-webhook').length).toBe(1);
  });

  it('does not restore a disabled built-in notification from dynamic metadata', async () => {
    SETTINGS.notifications = {
      email: { enabled: false },
      githubStatus: { enabled: false },
      googlechat: { enabled: false },
      microsoftteams: { enabled: false },
      pubsub: { enabled: false },
      slack: { enabled: false },
      sms: { enabled: false },
      cdevents: { enabled: false },
    } as INotificationSettings;
    initializeRuntimeMetadata(runtime);
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'SLACK', uiType: 'BASIC', parameters: [] },
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters: [] },
      ]),
    );

    await initializeDynamicRuntimeMetadata();
    await initializeDynamicRuntimeMetadata();

    const notificationKeys = Registry.pipeline.getNotificationTypes().map(({ key }) => key);
    expect(notificationKeys.some((key) => key.toLowerCase() === 'slack')).toBeFalse();
    expect(notificationKeys.filter((key) => key === 'runtime-basic').length).toBe(1);
  });

  it('logs a dynamic metadata failure once and resolves', async () => {
    const failure = new Error('metadata unavailable');
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.reject(failure));
    const consoleError = spyOn(console, 'error').and.stub();

    await initializeDynamicRuntimeMetadata();

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to load notification type metadata', failure);
  });

  it('logs preconfigured metadata failures independently and resolves', async () => {
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.resolve([]));
    metadataGet.and.callFake(({ url }) =>
      url.endsWith('/jobs/preconfigured') || url.endsWith('/webhooks/preconfigured')
        ? Promise.reject(new Error(url))
        : Promise.resolve([]),
    );
    const consoleError = spyOn(console, 'error').and.stub();

    await initializeDynamicRuntimeMetadata();

    expect(consoleError).toHaveBeenCalledTimes(2);
    expect(consoleError).toHaveBeenCalledWith('Failed to load preconfigured job stage metadata', jasmine.any(Error));
    expect(consoleError).toHaveBeenCalledWith(
      'Failed to load preconfigured webhook stage metadata',
      jasmine.any(Error),
    );
  });
});
