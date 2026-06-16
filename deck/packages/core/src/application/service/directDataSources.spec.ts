import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { registerApplicationConfigDataSource } from '../config/appConfig.dataSource';
import { navigationCategoryRegistry } from '../nav/navigationCategory.registry';
import { SETTINGS } from '../../config/settings';
import { registerCiDataSources } from '../../ci/ci.dataSource';
import { registerDeployboardDataSource } from '../../deployboard/deployboard.dataSource';
import { registerManagedResourcesDataSources } from '../../managed/managed.dataSource';
import { registerProfilerDataSource } from '../../profiler/profiler.dataSource';
import { registerServerGroupManagerDataSource } from '../../serverGroupManager/serverGroupManager.dataSource';

describe('direct application data source registration', () => {
  const originalFeatureSettings = SETTINGS.feature;

  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    navigationCategoryRegistry.clearCategories();
    SETTINGS.feature = { ...originalFeatureSettings, ci: true, managedResources: true };
  });

  afterEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    navigationCategoryRegistry.clearCategories();
    SETTINGS.feature = originalFeatureSettings;
  });

  it('registers application config without Angular module execution', () => {
    registerApplicationConfigDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual(['config']);
  });

  it('registers profiler without Angular module execution', () => {
    registerProfilerDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual(['jfrDumps']);
  });

  it('registers server group manager without Angular module execution', () => {
    registerServerGroupManagerDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual([
      'serverGroupManagers',
    ]);
  });

  it('registers managed resources when enabled without Angular module execution', () => {
    registerManagedResourcesDataSources();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual([
      'environments',
      'managedResources',
    ]);
  });

  it('does not register managed resources when disabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, managedResources: false };

    registerManagedResourcesDataSources();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([]);
  });

  it('registers CI data sources when enabled without Angular module execution', () => {
    registerCiDataSources();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual([
      'integration',
      'builds',
      'runningBuilds',
    ]);
    expect(navigationCategoryRegistry.getAll().filter((category) => category.key === 'integration').length).toBe(1);
  });

  it('does not register CI data sources when disabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, ci: false };

    registerCiDataSources();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([]);
  });

  it('registers deployboard without Angular module execution', () => {
    registerDeployboardDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual(['deployboard']);
    expect(navigationCategoryRegistry.getAll().filter((category) => category.key === 'deployments').length).toBe(1);
  });
});
