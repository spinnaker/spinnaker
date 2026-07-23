import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { INotificationSettings } from '../config';
import { SETTINGS } from '../config/settings';
import { NotificationService } from '../notification/NotificationService';
import { ScriptStageConfig } from '../pipeline/config/stages/script/ScriptStageConfig';
import { Registry } from '../registry';
import type { IFilterType } from '../search/widgets/SearchFilterTypeRegistry';
import { SearchFilterTypeRegistry } from '../search/widgets/SearchFilterTypeRegistry';

import {
  initializeDynamicRuntimeMetadata,
  initializeRuntimeMetadata,
  registerRuntimeDataSources,
} from './runtimeInitializers';

function replaceSearchFilterTypes(filterTypes: IFilterType[]): void {
  (SearchFilterTypeRegistry as any).registry.clear();
  filterTypes.forEach((filterType) => SearchFilterTypeRegistry.register(filterType));
}

describe('runtime initializers', () => {
  const originalFeatureSettings = SETTINGS.feature;
  const originalHiddenStages = SETTINGS.hiddenStages;
  const originalNotifications = SETTINGS.notifications;
  let originalDataSources: ReturnType<typeof ApplicationDataSourceRegistry.getDataSources>;
  let originalPipeline: typeof Registry.pipeline;
  let originalSearchFilterTypes: IFilterType[];
  let originalUrlBuilder: typeof Registry.urlBuilder;
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
    originalDataSources = ApplicationDataSourceRegistry.getDataSources();
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
  });

  afterEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    originalDataSources.forEach((dataSource) => ApplicationDataSourceRegistry.registerDataSource(dataSource));
    Registry.pipeline = originalPipeline;
    Registry.urlBuilder = originalUrlBuilder;
    replaceSearchFilterTypes(originalSearchFilterTypes);
    SETTINGS.feature = originalFeatureSettings;
    SETTINGS.hiddenStages = originalHiddenStages;
    SETTINGS.notifications = originalNotifications;
  });

  it('registers each enabled data source once in stable registration order', () => {
    const registerDataSource = spyOn(ApplicationDataSourceRegistry, 'registerDataSource').and.callThrough();

    registerRuntimeDataSources();
    registerRuntimeDataSources();

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    enabledKeys.forEach((key) => expect(keys.filter((registeredKey) => registeredKey === key).length).toBe(1));
    expect(registerDataSource.calls.allArgs().map(([config]) => config.key)).toEqual(enabledKeys);
  });

  it('omits gated data sources while retaining standard data sources', () => {
    SETTINGS.feature = { ...SETTINGS.feature, entityTags: false, functions: false };

    registerRuntimeDataSources();

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    expect(keys).not.toContain('functions');
    expect(keys).not.toContain('entityTags');
    expect(keys.slice().sort()).toEqual(enabledKeys.filter((key) => !['functions', 'entityTags'].includes(key)).sort());
  });

  it('registers synchronous runtime metadata exactly once without loading dynamic metadata', () => {
    const getNotificationTypeMetadata = spyOn(NotificationService, 'getNotificationTypeMetadata');

    initializeRuntimeMetadata();
    initializeRuntimeMetadata();

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

  it('registers supported dynamic notification metadata exactly once', async () => {
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters: [] },
        { notificationType: 'runtime-custom', uiType: 'CUSTOM', parameters: [] },
      ]),
    );

    await initializeDynamicRuntimeMetadata();
    await initializeDynamicRuntimeMetadata();

    const notificationTypes = Registry.pipeline.getNotificationTypes();
    expect(notificationTypes.filter(({ key }) => key === 'runtime-basic').length).toBe(1);
    expect(notificationTypes.find(({ key }) => key === 'runtime-custom')).toBeUndefined();
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
    initializeRuntimeMetadata();
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
});
