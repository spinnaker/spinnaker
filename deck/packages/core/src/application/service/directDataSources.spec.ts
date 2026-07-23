import type { IRootScopeService } from 'angular';
import { mock } from 'angular';

import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { ApplicationDataSource } from './applicationDataSource';
import { AngularServices } from '../../angular/services';
import { mockHttpClient } from '../../api/mock/jasmine';
import { registerApplicationConfigDataSource } from '../config/appConfig.dataSource';
import { navigationCategoryRegistry } from '../nav/navigationCategory.registry';
import { SETTINGS } from '../../config/settings';
import { registerCiDataSources } from '../../ci/ci.dataSource';
import { registerEntityTagsDataSource } from '../../entityTag/entityTags.dataSource';
import { registerFunctionDataSource } from '../../function/function.dataSource';
import { registerManagedResourcesDataSources } from '../../managed/managed.dataSource';
import * as loadBalancerDataSource from '../../loadBalancer/loadBalancer.dataSource';
import * as securityGroupDataSource from '../../securityGroup/securityGroup.dataSource';
import * as serverGroupDataSource from '../../serverGroup/serverGroup.dataSource';
import { registerServerGroupManagerDataSource } from '../../serverGroupManager/serverGroupManager.dataSource';
import { registerTaskDataSources } from '../../task/task.dataSource';
import { registerPipelineDataSources } from '../../pipeline/pipeline.dataSource';
import { ExecutionService } from '../../pipeline/service/execution.service';
import { Application } from '../application.model';

function getDataSourcesByKey(key: string): any[] {
  return ApplicationDataSourceRegistry.getDataSources().filter((dataSource) => dataSource.key === key);
}

describe('direct application data source registration', () => {
  const originalFeatureSettings = SETTINGS.feature;
  let $rootScope: IRootScopeService;

  beforeEach(
    mock.inject((_$rootScope_: IRootScopeService) => {
      $rootScope = _$rootScope_;
    }),
  );

  async function flushPromise<T>(promise: PromiseLike<T>): Promise<T> {
    const nativePromise = Promise.resolve(promise);
    await Promise.resolve();
    $rootScope.$digest();
    return nativePromise;
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

  it('registers application config without Angular module execution', () => {
    registerApplicationConfigDataSource();

    expect(ApplicationDataSourceRegistry.getDataSources().map((dataSource) => dataSource.key)).toEqual(['config']);
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

  it('registers functions once without Angular module execution', () => {
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
    spyOnProperty(AngularServices, 'providerServiceDelegate', 'get').and.returnValue({
      hasDelegate: () => true,
      getDelegate,
    } as any);
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
    spyOnProperty(AngularServices, 'providerServiceDelegate', 'get').and.returnValue({
      hasDelegate: () => true,
      getDelegate,
    } as any);
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

  it('registers entity tags once without Angular module execution', () => {
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

  it('registers tasks without Angular module execution', () => {
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

  it('resolves ready and refresh without Angular $q injection', async () => {
    const dataSource = new ApplicationDataSource({ key: 'example', defaultData: [] }, {} as any);

    await flushPromise(dataSource.refresh());
    dataSource.disabled = true;
    await flushPromise(dataSource.ready());

    expect(dataSource.data).toEqual([]);
  });

  it('refreshes an application without Angular $q injection', async () => {
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
    const executionService = new ExecutionService(null);
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
    application.executions.onRefresh(null, (executions: any[]) => refreshedExecutions.push(executions));

    application.runningExecutions.activate();
    await flushPromise(application.runningExecutions.ready());

    expect(application.executions.data).toEqual([runningExecution]);
    expect(refreshedExecutions).toEqual([[runningExecution]]);
  });
});

describe('server group Angular-compatible registration', () => {
  const pluginDataSource = {
    key: 'serverGroups',
    label: 'Plugin clusters',
    defaultData: [],
    pluginMarker: true,
  } as any;
  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    ApplicationDataSourceRegistry.registerDataSource(pluginDataSource);
  });

  beforeEach(
    mock.module(serverGroupDataSource.SERVER_GROUP_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
      $provide.value('clusterService', {});
    }),
  );

  beforeEach(mock.inject(() => undefined));

  afterEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
  });

  it('keeps one plugin server-group key when the Angular run block follows', () => {
    const serverGroupDataSources = ApplicationDataSourceRegistry.getDataSources().filter(
      ({ key }) => key === 'serverGroups',
    );

    expect(serverGroupDataSources).toEqual([pluginDataSource]);
  });
});

describe('load balancer Angular-compatible registration', () => {
  const pluginDataSource = { key: 'loadBalancers', pluginMarker: true } as any;
  let angularReader: any;

  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    angularReader = {
      loadLoadBalancers: jasmine.createSpy('angularLoadLoadBalancers').and.returnValue(Promise.resolve([])),
    };
  });

  afterEach(() => ApplicationDataSourceRegistry.clearDataSources());

  it('registers one data source across repeated direct calls using direct defaults', () => {
    const directLoadResult = Promise.resolve([]);
    const directReader = {
      loadLoadBalancers: jasmine.createSpy('directLoadLoadBalancers').and.returnValue(directLoadResult),
    };
    const whenResult = {} as PromiseLike<any>;
    const directQ = { when: jasmine.createSpy('directWhen').and.returnValue(whenResult) } as any;
    const readerAccessor = spyOnProperty(AngularServices, 'loadBalancerReader', 'get').and.returnValue(
      directReader as any,
    );
    const qAccessor = spyOnProperty(AngularServices, '$q', 'get').and.returnValue(directQ);

    loadBalancerDataSource.registerLoadBalancerDataSource();
    loadBalancerDataSource.registerLoadBalancerDataSource();

    const dataSources = getDataSourcesByKey('loadBalancers');
    expect(dataSources.length).toBe(1);
    expect(dataSources[0].loader({ name: 'app' } as Application)).toBe(directLoadResult);
    expect(dataSources[0].onLoad({ name: 'app' } as Application, [])).toBe(whenResult);
    expect(directReader.loadLoadBalancers).toHaveBeenCalledWith('app');
    expect(readerAccessor).toHaveBeenCalledTimes(1);
    expect(qAccessor).toHaveBeenCalledTimes(1);
  });

  describe('when direct registration precedes Angular activation', () => {
    let directDataSource: any;
    let directReader: any;
    let directQ: any;
    let angularQ: any;
    let selectedQ: any;

    beforeEach(() => {
      directReader = {
        loadLoadBalancers: jasmine.createSpy('directLoadLoadBalancers').and.returnValue(Promise.resolve([])),
      };
      directQ = { when: jasmine.createSpy('directWhen').and.returnValue({ source: 'direct' }) };
      angularQ = { when: jasmine.createSpy('angularWhen').and.returnValue({ source: 'angular' }) };
      selectedQ = directQ;
      spyOnProperty(AngularServices, 'loadBalancerReader', 'get').and.returnValue(directReader);
      spyOnProperty(AngularServices, '$q', 'get').and.callFake(() => selectedQ);
      loadBalancerDataSource.registerLoadBalancerDataSource();
      directDataSource = getDataSourcesByKey('loadBalancers')[0];
    });

    beforeEach(
      mock.module(loadBalancerDataSource.LOAD_BALANCER_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('loadBalancerReader', angularReader);
      }),
    );
    beforeEach(
      mock.inject(() => {
        selectedQ = angularQ;
      }),
    );

    it('preserves the direct config and dependencies selected at registration', () => {
      const dataSources = getDataSourcesByKey('loadBalancers');
      dataSources[0].loader({ name: 'app' } as Application);
      const loadBalancers: any[] = [];
      const onLoadResult = dataSources[0].onLoad({ name: 'app' } as Application, loadBalancers);

      expect(dataSources).toEqual([directDataSource]);
      expect(directReader.loadLoadBalancers).toHaveBeenCalledWith('app');
      expect(angularReader.loadLoadBalancers).not.toHaveBeenCalled();
      expect(onLoadResult).toEqual({ source: 'direct' });
      expect(directQ.when).toHaveBeenCalledWith(loadBalancers);
      expect(angularQ.when).not.toHaveBeenCalled();
    });
  });

  describe('when plugin registration precedes Angular activation', () => {
    beforeEach(() => ApplicationDataSourceRegistry.registerDataSource(pluginDataSource));
    beforeEach(
      mock.module(loadBalancerDataSource.LOAD_BALANCER_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('loadBalancerReader', angularReader);
      }),
    );
    beforeEach(mock.inject(() => undefined));

    it('preserves the plugin config', () => {
      expect(getDataSourcesByKey('loadBalancers')).toEqual([pluginDataSource]);
    });
  });

  describe('when Angular activation precedes direct registration', () => {
    let $q: ng.IQService;

    beforeEach(
      mock.module(loadBalancerDataSource.LOAD_BALANCER_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('loadBalancerReader', angularReader);
      }),
    );
    beforeEach(
      mock.inject((_$q_: ng.IQService) => {
        $q = _$q_;
      }),
    );

    it('preserves the Angular config and its injected dependencies', () => {
      const angularDataSource = getDataSourcesByKey('loadBalancers')[0];
      const loadResult = Promise.resolve([]);
      angularReader.loadLoadBalancers.and.returnValue(loadResult);
      const whenResult = {} as PromiseLike<any>;
      const when = spyOn($q, 'when').and.returnValue(whenResult as any);

      loadBalancerDataSource.registerLoadBalancerDataSource();
      expect(angularDataSource.loader({ name: 'app' } as Application)).toBe(loadResult);
      expect(angularDataSource.onLoad({ name: 'app' } as Application, [])).toBe(whenResult);

      expect(getDataSourcesByKey('loadBalancers')).toEqual([angularDataSource]);
      expect(angularReader.loadLoadBalancers).toHaveBeenCalledWith('app');
      expect(when).toHaveBeenCalledWith([]);
    });
  });
});

describe('security group Angular-compatible registration', () => {
  const pluginDataSource = { key: 'securityGroups', pluginMarker: true } as any;
  let angularReader: any;

  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    angularReader = {
      loadSecurityGroupsByApplicationName: jasmine.createSpy('angularLoadSecurityGroups'),
      getApplicationSecurityGroups: jasmine.createSpy('angularGetApplicationSecurityGroups'),
    };
  });

  afterEach(() => ApplicationDataSourceRegistry.clearDataSources());

  it('registers one data source across repeated direct calls using direct defaults', () => {
    const directQ = {} as ng.IQService;
    const providerServiceDelegate = {} as any;
    const qAccessor = spyOnProperty(AngularServices, '$q', 'get').and.returnValue(directQ);
    const delegateAccessor = spyOnProperty(AngularServices, 'providerServiceDelegate', 'get').and.returnValue(
      providerServiceDelegate,
    );

    securityGroupDataSource.registerSecurityGroupDataSource();
    securityGroupDataSource.registerSecurityGroupDataSource();

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
    expect(qAccessor).toHaveBeenCalledTimes(1);
    expect(delegateAccessor).toHaveBeenCalledTimes(2);
  });

  describe('when direct registration precedes Angular activation', () => {
    let directDataSource: any;

    beforeEach(() => {
      securityGroupDataSource.registerSecurityGroupDataSource();
      directDataSource = getDataSourcesByKey('securityGroups')[0];
    });
    beforeEach(
      mock.module(securityGroupDataSource.SECURITY_GROUP_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('securityGroupReader', angularReader);
      }),
    );
    beforeEach(mock.inject(() => undefined));

    it('preserves the direct config', () => {
      expect(getDataSourcesByKey('securityGroups')).toEqual([directDataSource]);
    });
  });

  describe('when plugin registration precedes Angular activation', () => {
    beforeEach(() => ApplicationDataSourceRegistry.registerDataSource(pluginDataSource));
    beforeEach(
      mock.module(securityGroupDataSource.SECURITY_GROUP_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('securityGroupReader', angularReader);
      }),
    );
    beforeEach(mock.inject(() => undefined));

    it('preserves the plugin config', () => {
      expect(getDataSourcesByKey('securityGroups')).toEqual([pluginDataSource]);
    });
  });

  describe('when Angular activation precedes direct registration', () => {
    beforeEach(
      mock.module(securityGroupDataSource.SECURITY_GROUP_DATA_SOURCE, ($provide: ng.auto.IProvideService) => {
        $provide.value('securityGroupReader', angularReader);
      }),
    );
    beforeEach(mock.inject(() => undefined));

    it('preserves the Angular config and its injected reader', () => {
      const application = { name: 'app' } as Application;
      const securityGroups = [];
      const loadResult = {} as PromiseLike<any>;
      const onLoadResult = {} as PromiseLike<any>;
      angularReader.loadSecurityGroupsByApplicationName.and.returnValue(loadResult);
      angularReader.getApplicationSecurityGroups.and.returnValue(onLoadResult);
      const angularDataSource = getDataSourcesByKey('securityGroups')[0];

      expect(angularDataSource.loader(application)).toBe(loadResult);
      expect(angularDataSource.onLoad(application, securityGroups)).toBe(onLoadResult);
      securityGroupDataSource.registerSecurityGroupDataSource();

      expect(getDataSourcesByKey('securityGroups')).toEqual([angularDataSource]);
      expect(angularReader.loadSecurityGroupsByApplicationName).toHaveBeenCalledWith('app');
      expect(angularReader.getApplicationSecurityGroups).toHaveBeenCalledWith(application, securityGroups);
    });
  });
});
