import { UIRouterReact } from '@uirouter/react';

import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { ApplicationDataSource } from './applicationDataSource';
import { mockHttpClient } from '../../api/mock/jasmine';
import { registerApplicationConfigDataSource } from '../config/appConfig.dataSource';
import { navigationCategoryRegistry } from '../nav/navigationCategory.registry';
import { SETTINGS } from '../../config/settings';
import { registerCiDataSources } from '../../ci/ci.dataSource';
import { registerEntityTagsDataSource } from '../../entityTag/entityTags.dataSource';
import {
  createDirectFunctionReader,
  registerFunctionDataSource as registerFunctionDataSourceImpl,
} from '../../function/function.dataSource';
import { registerManagedResourcesDataSources } from '../../managed/managed.dataSource';
import * as loadBalancerDataSourceImpl from '../../loadBalancer/loadBalancer.dataSource';
import * as securityGroupDataSourceImpl from '../../securityGroup/securityGroup.dataSource';
import * as serverGroupDataSourceImpl from '../../serverGroup/serverGroup.dataSource';
import { registerServerGroupManagerDataSource } from '../../serverGroupManager/serverGroupManager.dataSource';
import { registerTaskDataSources as registerTaskDataSourcesImpl } from '../../task/task.dataSource';
import { registerPipelineDataSources as registerPipelineDataSourcesImpl } from '../../pipeline/pipeline.dataSource';
import { ExecutionService } from '../../pipeline/service/execution.service';
import { getDirectRouter, setDirectRouter } from '../../navigation/directRouter';
import { Application } from '../application.model';
import { createDeckRuntime } from '../../bootstrap/DeckRuntime';

const testRuntime = createDeckRuntime(new UIRouterReact());
const loadBalancerDataSource = {
  ...loadBalancerDataSourceImpl,
  registerLoadBalancerDataSource: (
    promiseService = testRuntime.promiseService,
    reader = testRuntime.services.loadBalancerReader,
  ) => loadBalancerDataSourceImpl.registerLoadBalancerDataSource(promiseService, reader),
};
const securityGroupDataSource = {
  ...securityGroupDataSourceImpl,
  registerSecurityGroupDataSource: (reader = testRuntime.services.securityGroupReader) =>
    securityGroupDataSourceImpl.registerSecurityGroupDataSource(reader),
};
const serverGroupDataSource = {
  ...serverGroupDataSourceImpl,
  registerServerGroupDataSource: (service = testRuntime.services.clusterService) =>
    serverGroupDataSourceImpl.registerServerGroupDataSource(service),
};

function registerFunctionDataSource(): void {
  registerFunctionDataSourceImpl(createDirectFunctionReader(testRuntime.services.providerServiceDelegate));
}

function registerTaskDataSources(
  promiseService = testRuntime.promiseService,
  clusterService = testRuntime.services.clusterService,
) {
  return registerTaskDataSourcesImpl(promiseService, clusterService);
}

function registerPipelineDataSources(
  promiseService = testRuntime.promiseService,
  executionService = testRuntime.services.executionService,
  clusterService = testRuntime.services.clusterService,
) {
  return registerPipelineDataSourcesImpl(promiseService, executionService, clusterService);
}

function getDataSourcesByKey(key: string): any[] {
  return ApplicationDataSourceRegistry.getDataSources().filter((dataSource) => dataSource.key === key);
}

describe('direct application data source registration', () => {
  const originalFeatureSettings = SETTINGS.feature;

  function flushPromise<T>(promise: PromiseLike<T>): Promise<T> {
    return Promise.resolve(promise);
  }

  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    navigationCategoryRegistry.clearCategories();
    SETTINGS.feature = {
      ...originalFeatureSettings,
      ci: true,
      managedResources: true,
      pipelines: true,
    };
  });

  afterEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    navigationCategoryRegistry.clearCategories();
    SETTINGS.feature = originalFeatureSettings;
  });

  it('registers application config', () => {
    registerApplicationConfigDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual(['config']);
  });

  it('registers server group manager directly', () => {
    registerServerGroupManagerDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual([
      'serverGroupManagers',
    ]);
  });

  it('registers managed resources when enabled', () => {
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

  it('registers CI data sources when enabled', () => {
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

  it('registers functions once directly', () => {
    SETTINGS.feature = { ...SETTINGS.feature, functions: true };

    registerFunctionDataSource();
    registerFunctionDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([
      jasmine.objectContaining({
        key: 'functions',
        label: 'functions',
        sref: '.insight.functions',
        providerField: 'cloudProvider',
        credentialsField: 'account',
        regionField: 'region',
      }),
    ]);
  });

  it('uses the aws fallback for direct function set transformers', async () => {
    const http = mockHttpClient();
    const normalizeFunctionSet = jasmine.createSpy('normalizeFunctionSet').and.callFake((functions) => functions);
    const transformer = { normalizeFunction: (functionDef: any) => functionDef, normalizeFunctionSet };
    const getDelegate = jasmine.createSpy('getDelegate').and.callFake((provider: string) => {
      if (provider !== 'aws') {
        throw new Error(`Expected aws provider, received ${provider}`);
      }
      return transformer;
    });
    spyOn(testRuntime.services.providerServiceDelegate, 'hasDelegate').and.returnValue(true);
    spyOn(testRuntime.services.providerServiceDelegate, 'getDelegate').and.callFake(getDelegate);
    SETTINGS.feature = { ...SETTINGS.feature, functions: true };
    http.expectGET('/applications/app/functions').respond(200, [{ name: 'function-with-default-provider' }]);

    registerFunctionDataSource();
    const config = ApplicationDataSourceRegistry.getDataSources().find(({ key }) => key === 'functions');
    const loadPromise = config.loader({ name: 'app' } as Application);
    await http.flush();
    await loadPromise;

    expect(getDelegate.calls.allArgs()).toContain(['aws', 'function.setTransformer']);
    expect(normalizeFunctionSet).toHaveBeenCalledTimes(1);
  });

  it('calls each direct function set transformer once with its owning instance', async () => {
    class ContextAwareTransformer {
      public normalizeSetCalls = 0;

      constructor(private marker: string) {}

      public normalizeFunction(functionDef: any): any {
        return functionDef;
      }

      public normalizeFunctionSet(functions: any[]): any[] {
        this.normalizeSetCalls++;
        return functions.map((functionDef) => ({ ...functionDef, normalizedBy: this.marker }));
      }
    }

    const http = mockHttpClient();
    const setTransformers: ContextAwareTransformer[] = [];
    const getDelegate = jasmine.createSpy('getDelegate').and.callFake((_provider: string, serviceKey: string) => {
      const transformer = new ContextAwareTransformer(`transformer-${setTransformers.length + 1}`);
      if (serviceKey === 'function.setTransformer') {
        setTransformers.push(transformer);
      }
      return transformer;
    });
    spyOn(testRuntime.services.providerServiceDelegate, 'hasDelegate').and.returnValue(true);
    spyOn(testRuntime.services.providerServiceDelegate, 'getDelegate').and.callFake(getDelegate);
    SETTINGS.feature = { ...SETTINGS.feature, functions: true };
    http.expectGET('/applications/app/functions').respond(200, [
      { name: 'function-one', provider: 'aws' },
      { name: 'function-two', provider: 'aws' },
    ]);

    registerFunctionDataSource();
    const config = ApplicationDataSourceRegistry.getDataSources().find(({ key }) => key === 'functions');
    const loadPromise = config.loader({ name: 'app' } as Application);
    await http.flush();
    const functions = await loadPromise;

    expect(setTransformers.map(({ normalizeSetCalls }) => normalizeSetCalls)).toEqual([1, 0]);
    expect(functions.map(({ normalizedBy }) => normalizedBy)).toEqual(['transformer-1', 'transformer-1']);
  });

  it('registers entity tags once', () => {
    SETTINGS.feature = { ...SETTINGS.feature, entityTags: true };

    registerEntityTagsDataSource();
    registerEntityTagsDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([
      jasmine.objectContaining({ key: 'entityTags', visible: false }),
    ]);
  });

  it('preserves function and entity-tag feature gates for direct registration', () => {
    SETTINGS.feature = { ...SETTINGS.feature, entityTags: false, functions: false };

    registerFunctionDataSource();
    registerEntityTagsDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([]);
  });

  it('registers tasks directly', () => {
    registerTaskDataSources(undefined, { addTasksToServerGroups: jasmine.createSpy('addTasksToServerGroups') });

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual([
      'tasks',
      'runningTasks',
    ]);
    expect(ApplicationDataSourceRegistry.getDataSources()[0]).toEqual(
      jasmine.objectContaining({ key: 'tasks', sref: '.tasks', badge: 'runningTasks' }),
    );
  });

  it('registers each task data source once when called repeatedly', () => {
    const clusterService = { addTasksToServerGroups: jasmine.createSpy('addTasksToServerGroups') };

    registerTaskDataSources(undefined, clusterService);
    registerTaskDataSources(undefined, clusterService);

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    expect(keys.filter((key) => key === 'tasks').length).toBe(1);
    expect(keys.filter((key) => key === 'runningTasks').length).toBe(1);
  });

  it('repairs a partial task data-source registration without replacing it', () => {
    const existingRunningTasks = { key: 'runningTasks', visible: false, defaultData: ['plugin'] };
    ApplicationDataSourceRegistry.registerDataSource(existingRunningTasks);

    registerTaskDataSources(undefined, { addTasksToServerGroups: jasmine.createSpy('addTasksToServerGroups') });

    const dataSources = ApplicationDataSourceRegistry.getDataSources();
    expect(dataSources.filter(({ key }) => key === 'tasks').length).toBe(1);
    expect(dataSources.filter(({ key }) => key === 'runningTasks').length).toBe(1);
    expect(dataSources.find(({ key }) => key === 'runningTasks')).toEqual(existingRunningTasks);
  });

  it('registers each pipeline data source once when called repeatedly', () => {
    const executionService = {};
    const clusterService = {};

    registerPipelineDataSources(undefined, executionService, clusterService);
    registerPipelineDataSources(undefined, executionService, clusterService);

    const keys = ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key);
    ['executions', 'pipelineConfigs', 'runningExecutions'].forEach((key) => {
      expect(keys.filter((registeredKey) => registeredKey === key).length).toBe(1);
    });
  });

  it('repairs a partial pipeline data-source registration without replacing it', () => {
    const existingPipelineConfigs = { key: 'pipelineConfigs', visible: false, defaultData: ['plugin'] };
    ApplicationDataSourceRegistry.registerDataSource(existingPipelineConfigs);

    registerPipelineDataSources(undefined, {}, {});

    const dataSources = ApplicationDataSourceRegistry.getDataSources();
    ['executions', 'pipelineConfigs', 'runningExecutions'].forEach((key) => {
      expect(dataSources.filter((dataSource) => dataSource.key === key).length).toBe(1);
    });
    expect(dataSources.find(({ key }) => key === 'pipelineConfigs')).toEqual(existingPipelineConfigs);
  });

  it('resolves ready and refresh with the runtime promise service', async () => {
    const dataSource = new ApplicationDataSource({ key: 'example', defaultData: [] }, {} as any);

    await flushPromise(dataSource.refresh());
    dataSource.disabled = true;
    await flushPromise(dataSource.ready());

    expect(dataSource.data).toEqual([]);
  });

  it('registers auto-activation hooks with the direct router', () => {
    const previousRouter = getDirectRouter();
    const onSuccess = jasmine.createSpy('onSuccess');

    try {
      setDirectRouter({ transitionService: { onSuccess } } as any);

      new ApplicationDataSource(
        { key: 'example', defaultData: [], activeState: '**.example.**', autoActivate: true },
        {} as any,
      );

      expect(onSuccess.calls.allArgs()).toEqual([
        [{ entering: '**.example.**' }, jasmine.any(Function)],
        [{ exiting: '**.example.**' }, jasmine.any(Function)],
      ]);
    } finally {
      setDirectRouter(previousRouter);
    }
  });

  it('allows auto-activated data sources to be created before the direct router', () => {
    const previousRouter = getDirectRouter();

    try {
      setDirectRouter(null);

      expect(
        () =>
          new ApplicationDataSource(
            { key: 'example', defaultData: [], activeState: '**.example.**', autoActivate: true },
            {} as any,
          ),
      ).not.toThrow();
    } finally {
      setDirectRouter(previousRouter);
    }
  });

  it('refreshes an application with the runtime promise service', async () => {
    const application = new Application('example', {} as any, [{ key: 'example', defaultData: [] }]);

    await flushPromise(application.refresh());
    await flushPromise(application.ready());

    expect(application.example.data).toEqual([]);
  });

  it('loads running executions without a serverGroups data source', async () => {
    const executionService = {
      getRunningExecutions: () => Promise.resolve([]),
      transformExecutions: () => undefined,
      mergeRunningExecutionsIntoExecutions: () => undefined,
    };
    const clusterService = { addExecutionsToServerGroups: jasmine.createSpy('addExecutionsToServerGroups') };
    registerPipelineDataSources(undefined, executionService, clusterService);
    const application = new Application('example', {} as any, ApplicationDataSourceRegistry.getDataSources());

    application.runningExecutions.activate();
    await flushPromise(application.runningExecutions.ready());

    expect(application.runningExecutions.data).toEqual([]);
    expect(clusterService.addExecutionsToServerGroups).not.toHaveBeenCalled();
  });

  it('merges previously loaded running executions after the executions data source loads', async () => {
    const runningExecution = { id: 'running', status: 'RUNNING', isActive: true };
    const executionService = {
      getExecutions: () => Promise.resolve([]),
      getRunningExecutions: () => Promise.resolve([runningExecution]),
      transformExecutions: () => undefined,
      addExecutionsToApplication: (_application: Application, executions: any[]) => executions,
      removeCompletedExecutionsFromRunningData: () => undefined,
      mergeRunningExecutionsIntoExecutions: jasmine
        .createSpy('mergeRunningExecutionsIntoExecutions')
        .and.callFake((application: Application) =>
          application.executions.data.push(...application.runningExecutions.data),
        ),
    };
    const clusterService = { addExecutionsToServerGroups: jasmine.createSpy('addExecutionsToServerGroups') };
    registerPipelineDataSources(undefined, executionService, clusterService);
    const application = new Application('example', {} as any, ApplicationDataSourceRegistry.getDataSources());

    application.runningExecutions.activate();
    await flushPromise(application.runningExecutions.ready());
    executionService.mergeRunningExecutionsIntoExecutions.calls.reset();

    application.executions.activate();
    await flushPromise(application.executions.ready());

    expect(executionService.mergeRunningExecutionsIntoExecutions).toHaveBeenCalledWith(application);
    expect(application.executions.data).toEqual([runningExecution]);
  });

  it('notifies executions subscribers when running executions are merged', async () => {
    const runningExecution = { id: 'running', status: 'RUNNING', isActive: true, stringVal: 'running' };
    const executionService = new ExecutionService(null, null);
    spyOn(executionService, 'getExecutions').and.returnValue(Promise.resolve([]));
    spyOn(executionService, 'getRunningExecutions').and.returnValue(Promise.resolve([runningExecution]));
    spyOn(executionService, 'transformExecutions').and.callFake(() => undefined);
    spyOn(executionService, 'addExecutionsToApplication').and.callFake(
      (_application: Application, executions: any[]) => executions,
    );
    spyOn(executionService, 'removeCompletedExecutionsFromRunningData').and.callFake(() => undefined);
    const clusterService = { addExecutionsToServerGroups: jasmine.createSpy('addExecutionsToServerGroups') };
    registerPipelineDataSources(undefined, executionService, clusterService);
    const application = new Application('example', {} as any, ApplicationDataSourceRegistry.getDataSources());
    const refreshedExecutions: any[][] = [];

    application.executions.activate();
    await flushPromise(application.executions.ready());
    application.executions.onRefresh((executions: any[]) => refreshedExecutions.push(executions));

    application.runningExecutions.activate();
    await flushPromise(application.runningExecutions.ready());

    expect(application.executions.data).toEqual([runningExecution]);
    expect(refreshedExecutions).toEqual([[runningExecution]]);
  });
});

describe('load balancer direct registration', () => {
  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => ApplicationDataSourceRegistry.clearDataSources());

  it('registers one data source across repeated direct calls using direct defaults', () => {
    const directLoadResult = Promise.resolve([]);
    const directReader = {
      loadLoadBalancers: jasmine.createSpy('directLoadLoadBalancers').and.returnValue(directLoadResult),
    };
    const resolveResult = {} as PromiseLike<any>;
    const promiseService = { resolve: jasmine.createSpy('resolve').and.returnValue(resolveResult) } as any;
    loadBalancerDataSource.registerLoadBalancerDataSource(promiseService, directReader as any);
    loadBalancerDataSource.registerLoadBalancerDataSource(promiseService, directReader as any);

    const dataSources = getDataSourcesByKey('loadBalancers');
    expect(dataSources.length).toBe(1);
    expect(dataSources[0].loader({ name: 'app' } as Application)).toBe(directLoadResult);
    expect(dataSources[0].onLoad({ name: 'app' } as Application, [])).toBe(resolveResult);
    expect(directReader.loadLoadBalancers).toHaveBeenCalledWith('app');
  });
});

describe('security group direct registration', () => {
  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => ApplicationDataSourceRegistry.clearDataSources());

  it('registers one data source across repeated direct calls using direct defaults', () => {
    const directReader = {
      loadSecurityGroupsByApplicationName: jasmine.createSpy('directLoadSecurityGroups'),
      getApplicationSecurityGroups: jasmine.createSpy('directGetApplicationSecurityGroups'),
    } as any;

    securityGroupDataSource.registerSecurityGroupDataSource(directReader);
    securityGroupDataSource.registerSecurityGroupDataSource(directReader);

    expect(getDataSourcesByKey('securityGroups')).toEqual([
      jasmine.objectContaining({
        key: 'securityGroups',
        label: 'Firewalls',
        sref: '.insight.firewalls',
        providerField: 'provider',
        credentialsField: 'accountName',
        regionField: 'region',
      }),
    ]);
  });
});
