import { UIRouterReact } from '@uirouter/react';
import * as ngimport from 'ngimport';

import { AccountService } from '../account';
import { mockHttpClient } from '../api/mock/jasmine';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { InfrastructureCaches } from '../cache';
import { CloudProviderRegistry } from '../cloudProvider';
import { SETTINGS } from '../config';
import { registerLoadBalancerDataSource } from '../loadBalancer/loadBalancer.dataSource';
import { createLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { setDirectRouter } from '../navigation/directRouter';
import { AngularServices } from './services';

describe('AngularServices direct router fallback', () => {
  const provider = 'serverGroupCommandBuilderTest';

  afterEach(() => {
    setDirectRouter(null);
    (AngularServices as any).wrappedState = null;
    (AngularServices as any).directCacheInitializer = null;
    (AngularServices as any).directExecutionDetailsSectionService = null;
    (AngularServices as any).directLoadBalancerReader = null;
    (AngularServices as any).directSecurityGroupReader = null;
    (AngularServices as any).directServerGroupWriter = null;
    InfrastructureCaches.destroyCaches();
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
  });

  it('returns the active direct UI Router when Angular is not bootstrapped', () => {
    const router = new UIRouterReact();

    setDirectRouter(router);

    expect(AngularServices.$uiRouter).toBe(router as any);
  });

  it('reports the direct UI Router only while one is set', () => {
    const originalInjector = ngimport.$injector;
    const router = new UIRouterReact();
    (ngimport as any).$injector = undefined;

    try {
      expect(AngularServices.has('$uiRouter')).toBe(false);
      expect(AngularServices.has('unknownDirectService')).toBe(false);

      setDirectRouter(router);

      expect(AngularServices.has('$uiRouter')).toBe(true);
      expect(AngularServices.has('unknownDirectService')).toBe(false);

      setDirectRouter(null);

      expect(AngularServices.has('$uiRouter')).toBe(false);
    } finally {
      router.dispose();
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('preserves injector service presence before checking the direct UI Router', () => {
    const originalInjector = ngimport.$injector;
    const router = new UIRouterReact();
    const has = jasmine
      .createSpy('has')
      .and.callFake((serviceName: string) => serviceName === 'angularService' || serviceName === '$uiRouter');
    (ngimport as any).$injector = { has };
    setDirectRouter(router);

    try {
      expect(AngularServices.has('angularService')).toBe(true);
      expect(AngularServices.has('$uiRouter')).toBe(true);
      expect(AngularServices.has('unknownDirectService')).toBe(false);
      expect(has.calls.allArgs()).toEqual([['angularService'], ['$uiRouter'], ['unknownDirectService']]);
    } finally {
      setDirectRouter(null);
      router.dispose();
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('uses only the direct UI Router fallback when injector presence checks fail', () => {
    const originalInjector = ngimport.$injector;
    const router = new UIRouterReact();
    (ngimport as any).$injector = {
      has: jasmine.createSpy('has').and.throwError('injector unavailable'),
    };
    setDirectRouter(router);

    try {
      expect(AngularServices.has('$uiRouter')).toBe(true);
      expect(AngularServices.has('unknownDirectService')).toBe(false);
    } finally {
      setDirectRouter(null);
      router.dispose();
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('returns the direct router state service when Angular state is not available', () => {
    const router = new UIRouterReact();

    setDirectRouter(router);

    expect(AngularServices.$state).toBe(router.stateService as any);
  });

  it('returns direct router state params when Angular is not bootstrapped', () => {
    const router = new UIRouterReact();
    router.globals.params = { application: 'compute' } as any;

    setDirectRouter(router);

    expect(AngularServices.$stateParams.application).toBe('compute');
  });

  it('returns a direct infrastructure search service when Angular is not bootstrapped', () => {
    expect(AngularServices.infrastructureSearchService.getSearcher()).toBeDefined();
  });

  it('returns a direct page title service when Angular is not bootstrapped', () => {
    AngularServices.pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Search' } });

    expect(document.title).toBe('Search');
  });

  it('returns direct modal and cache fallbacks when Angular is not bootstrapped', async () => {
    const originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;
    spyOn(AngularServices.securityGroupReader, 'getAllSecurityGroups').and.returnValue(Promise.resolve([]));

    try {
      expect(AngularServices.modalService.open).toBeDefined();
      expect(() => AngularServices.modalStackService.dismissAll()).not.toThrow();
      await expectAsync(AngularServices.cacheInitializer.refreshCaches()).toBeResolved();
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('returns direct root scope and timeout fallbacks when Angular is not bootstrapped', async () => {
    const originalRootScope = ngimport.$rootScope;
    const originalTimeout = ngimport.$timeout;
    (ngimport as any).$rootScope = undefined;
    (ngimport as any).$timeout = undefined;
    const applied = jasmine.createSpy('applied');
    const timedOut = jasmine.createSpy('timedOut');

    try {
      AngularServices.$rootScope.$apply(applied);
      const timeout = AngularServices.$timeout(timedOut, 1000);
      AngularServices.$timeout.cancel(timeout);

      expect(applied).toHaveBeenCalled();
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(timedOut).not.toHaveBeenCalled();
    } finally {
      (ngimport as any).$rootScope = originalRootScope;
      (ngimport as any).$timeout = originalTimeout;
    }
  });

  it('returns a direct execution details section service when Angular is not bootstrapped', () => {
    const originalInjector = ngimport.$injector;
    const router = new UIRouterReact();
    setDirectRouter(router);
    (ngimport as any).$injector = undefined;

    try {
      expect(() => AngularServices.executionDetailsSectionService.synchronizeSection(['stage'])).not.toThrow();
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('returns a direct $q fallback with Angular-style promise helpers', async () => {
    const originalQ = ngimport.$q;
    (ngimport as any).$q = undefined;
    const directQ = AngularServices.$q as any;

    try {
      await expectAsync(directQ.when('when')).toBeResolvedTo('when');
      await expectAsync(directQ.resolve('resolve')).toBeResolvedTo('resolve');
      await expectAsync(directQ.all([Promise.resolve('all')])).toBeResolvedTo(['all']);
      await expectAsync(directQ((resolve: (value: string) => void) => resolve('callable'))).toBeResolvedTo('callable');

      const deferred = directQ.defer();
      deferred.resolve('deferred');

      await expectAsync(deferred.promise).toBeResolvedTo('deferred');
      await expectAsync(directQ.reject('reject')).toBeRejectedWith('reject');
    } finally {
      (ngimport as any).$q = originalQ;
    }
  });

  it('returns a direct interpolation fallback when Angular is not bootstrapped', () => {
    expect(
      AngularServices.$interpolate('service={{ service.name }} pod={{pod}}')({
        service: { name: 'octopus' },
        pod: 'octopus-main',
      }),
    ).toBe('service=octopus pod=octopus-main');
  });

  it('returns a direct insight filter state model when Angular is not bootstrapped', () => {
    const insightFilterStateModel = AngularServices.insightFilterStateModel;

    expect(insightFilterStateModel.filtersExpanded).toBe(true);

    insightFilterStateModel.pinFilters(false);

    expect(AngularServices.insightFilterStateModel.filtersExpanded).toBe(false);
  });

  it('builds server group commands from direct provider registry without Angular DI', async () => {
    const originalInjector = ngimport.$injector;
    const stage = { type: 'deploy' };
    const pipeline = { id: 'pipeline-1' };

    class TestServerGroupCommandBuilder {
      public buildNewServerGroupCommandForPipeline(currentStage: any, currentPipeline: any) {
        return Promise.resolve({ currentStage, currentPipeline });
      }
    }

    SETTINGS.providers[provider] = { enabled: true };
    CloudProviderRegistry.registerProvider(provider, {
      name: 'Test Provider',
      serverGroup: {
        commandBuilder: TestServerGroupCommandBuilder,
      },
    });
    (ngimport as any).$injector = undefined;

    try {
      const command = await AngularServices.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(
        provider,
        stage,
        pipeline,
      );

      expect(command).toEqual({ currentStage: stage, currentPipeline: pipeline });
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('converts server group commands through the direct provider transformer without Angular DI', () => {
    const originalInjector = ngimport.$injector;
    const command = { selectedProvider: provider, cluster: 'ecsapp-prod-ecsdemo' };

    class TestServerGroupTransformer {
      public convertServerGroupCommandToDeployConfiguration(base: any) {
        return { converted: true, base };
      }
    }

    SETTINGS.providers[provider] = { enabled: true };
    CloudProviderRegistry.registerProvider(provider, {
      name: 'Test Provider',
      serverGroup: {
        transformer: TestServerGroupTransformer,
      },
    });
    (ngimport as any).$injector = undefined;

    try {
      expect(AngularServices.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)).toEqual({
        converted: true,
        base: command,
      });
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('returns a direct security group reader when Angular is not bootstrapped', () => {
    const originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;

    try {
      expect(AngularServices.securityGroupReader.getAllSecurityGroups).toEqual(jasmine.any(Function));
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });

  it('returns a direct instance type service when Angular is not bootstrapped', () => {
    const originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;

    try {
      expect(AngularServices.instanceTypeService.getCategoryForMultipleInstanceTypes).toEqual(jasmine.any(Function));
    } finally {
      (ngimport as any).$injector = originalInjector;
    }
  });
});

describe('AngularServices direct|load balancer data source service accessors', () => {
  const provider = 'directServiceAccessorTest';
  let originalInjector: typeof ngimport.$injector;

  const resetDirectServices = () => {
    (AngularServices as any).directCacheInitializer = null;
    (AngularServices as any).directLoadBalancerReader = null;
    (AngularServices as any).directSecurityGroupReader = null;
    (AngularServices as any).directServerGroupWriter = null;
  };

  beforeEach(() => {
    originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;
    resetDirectServices();
  });

  afterEach(() => {
    (ngimport as any).$injector = originalInjector;
    resetDirectServices();
    InfrastructureCaches.destroyCaches();
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
  });

  it('AngularServices direct accessors lazily cache service instances', () => {
    expect(AngularServices.serverGroupWriter).toBe(AngularServices.serverGroupWriter);
    expect(AngularServices.cacheInitializer).toBe(AngularServices.cacheInitializer);
    expect(AngularServices.loadBalancerReader).toBe(AngularServices.loadBalancerReader);
  });

  it('AngularServices direct accessors propagate registered Angular service errors', () => {
    const sentinel = new Error('registered service failed');
    const get = jasmine.createSpy('get').and.callFake(() => {
      throw sentinel;
    });
    (ngimport as any).$injector = {
      has: jasmine.createSpy('has').and.returnValue(true),
      get,
    };

    expect(() => AngularServices.cacheInitializer).toThrow(sentinel);
    expect(() => AngularServices.loadBalancerReader).toThrow(sentinel);
    expect(() => AngularServices.serverGroupWriter).toThrow(sentinel);
    expect(get.calls.allArgs()).toEqual([['cacheInitializer'], ['loadBalancerReader'], ['serverGroupWriter']]);
  });

  it('AngularServices direct accessors do not resolve services the Angular injector does not have', () => {
    const get = jasmine.createSpy('get').and.throwError('unknown provider');
    const has = jasmine.createSpy('has').and.returnValue(false);
    (ngimport as any).$injector = { has, get };

    expect(AngularServices.cacheInitializer.initialize).toEqual(jasmine.any(Function));
    expect(AngularServices.loadBalancerReader.loadLoadBalancers).toEqual(jasmine.any(Function));
    expect(AngularServices.serverGroupWriter.destroyServerGroup).toEqual(jasmine.any(Function));
    expect(has.calls.allArgs()).toEqual([['cacheInitializer'], ['loadBalancerReader'], ['serverGroupWriter']]);
    expect(get).not.toHaveBeenCalled();
  });

  it('AngularServices direct cache initializer exposes working methods', async () => {
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve([]));
    const getAllSecurityGroups = spyOn(AngularServices.securityGroupReader, 'getAllSecurityGroups').and.returnValue(
      Promise.resolve([]),
    );
    const cacheInitializer = AngularServices.cacheInitializer;

    expect(cacheInitializer.initialize).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCache).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCaches).toEqual(jasmine.any(Function));

    await cacheInitializer.initialize();
    await cacheInitializer.refreshCache('securityGroups');
    await cacheInitializer.refreshCaches();

    expect(AccountService.listProviders).toHaveBeenCalledWith();
    expect(getAllSecurityGroups).toHaveBeenCalledTimes(3);
  });

  it('AngularServices direct server group writer submits destroy jobs', async () => {
    const serverGroupWriter = AngularServices.serverGroupWriter;
    const http = mockHttpClient();
    let submitted: any;
    http
      .expectPOST('/tasks')
      .respond(200, { ref: '/1' })
      .onRequestReceived((request) => (submitted = request.data));
    http.expectGET('/tasks/1').respond(200, {});

    const task = serverGroupWriter.destroyServerGroup(
      {
        name: 'app-test-v001',
        account: 'test-account',
        region: 'us-east-1',
        provider,
      } as any,
      { name: 'app' } as any,
    );
    await http.flush();
    await task;

    expect(submitted.job[0]).toEqual(
      jasmine.objectContaining({
        type: 'destroyServerGroup',
        serverGroupName: 'app-test-v001',
        credentials: 'test-account',
        region: 'us-east-1',
        cloudProvider: provider,
      }),
    );
  });

  it('AngularServices direct load balancer reader uses a registered provider transformer', async () => {
    class TestLoadBalancerTransformer {
      public normalizeLoadBalancer(loadBalancer: any) {
        return Promise.resolve({ ...loadBalancer, transformed: true });
      }
    }

    SETTINGS.providers[provider] = { enabled: true };
    CloudProviderRegistry.registerProvider(provider, {
      name: 'Direct Service Accessor Test',
      loadBalancer: { transformer: TestLoadBalancerTransformer },
    });
    const loadBalancerReader = AngularServices.loadBalancerReader;
    const http = mockHttpClient();
    http.expectGET('/applications/app/loadBalancers').respond(200, [
      {
        name: 'app-test-detail',
        provider,
      },
    ]);

    const loadBalancersPromise = loadBalancerReader.loadLoadBalancers('app');
    await http.flush();
    const loadBalancers = await loadBalancersPromise;

    expect(loadBalancers).toEqual([
      jasmine.objectContaining({
        name: 'app-test-detail',
        provider,
        cloudProvider: provider,
        stack: 'test',
        detail: 'detail',
        transformed: true,
      }),
    ]);
  });

  it('AngularServices direct load balancer transformer factory preserves provider and set normalization', async () => {
    const normalizeLoadBalancerSet = jasmine
      .createSpy('normalizeLoadBalancerSet')
      .and.callFake((loadBalancers: any[]) => loadBalancers.slice().reverse());
    const providerTransformer = {
      context: provider,
      normalizeLoadBalancer(loadBalancer: any) {
        return Promise.resolve({ ...loadBalancer, context: this.context });
      },
    };
    const providerSetTransformer = { normalizeLoadBalancerSet };
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.callFake((requestedProvider: string, serviceKey: string) => {
        return (
          requestedProvider === provider &&
          ['loadBalancer.transformer', 'loadBalancer.setTransformer'].includes(serviceKey)
        );
      }),
      getDelegate: jasmine.createSpy('getDelegate').and.callFake((_requestedProvider: string, serviceKey: string) => {
        return serviceKey === 'loadBalancer.transformer' ? providerTransformer : providerSetTransformer;
      }),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate as any);
    const loadBalancers = [
      { name: 'app-test-detail', provider },
      { name: 'app-test-other', provider },
    ];

    await expectAsync(transformer.normalizeLoadBalancer(loadBalancers[0])).toBeResolvedTo(
      jasmine.objectContaining({ context: provider }),
    );
    expect(transformer.normalizeLoadBalancerSet(loadBalancers)).toEqual(loadBalancers.slice().reverse());
    expect(normalizeLoadBalancerSet).toHaveBeenCalledTimes(1);
    expect(providerServiceDelegate.getDelegate).toHaveBeenCalledWith(provider, 'loadBalancer.transformer');
    expect(providerServiceDelegate.getDelegate).toHaveBeenCalledWith(provider, 'loadBalancer.setTransformer');
  });

  it('AngularServices direct load balancer transformer resolves each provider set transformer once', () => {
    const secondProvider = `${provider}Second`;
    const resolvedProviders: string[] = [];
    const createdDelegates: any[] = [];
    const invokedDelegates: any[] = [];
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.returnValue(true),
      getDelegate: jasmine.createSpy('getDelegate').and.callFake((requestedProvider: string) => {
        resolvedProviders.push(requestedProvider);
        const delegate = {
          normalizeLoadBalancerSet(loadBalancers: any[]) {
            invokedDelegates.push(this);
            return loadBalancers;
          },
        };
        createdDelegates.push(delegate);
        return delegate;
      }),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate as any);
    const loadBalancers = [
      { name: 'app-test-one', provider },
      { name: 'app-test-two', provider },
      { name: 'app-test-three', provider: secondProvider },
      { name: 'app-test-four', provider },
      { name: 'app-test-five', provider: secondProvider },
    ];

    expect(transformer.normalizeLoadBalancerSet(loadBalancers)).toBe(loadBalancers);
    expect(resolvedProviders).toEqual([provider, secondProvider]);
    expect(providerServiceDelegate.hasDelegate.calls.allArgs()).toEqual([
      [provider, 'loadBalancer.setTransformer'],
      [secondProvider, 'loadBalancer.setTransformer'],
    ]);
    expect(invokedDelegates).toEqual(createdDelegates);
  });

  it('AngularServices direct load balancer transformer leaves unregistered providers unchanged', async () => {
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.returnValue(false),
      getDelegate: jasmine.createSpy('getDelegate'),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate as any);
    const loadBalancer = { name: 'app-test-detail', provider };

    await expectAsync(transformer.normalizeLoadBalancer(loadBalancer)).toBeResolvedTo(loadBalancer);
    expect(providerServiceDelegate.getDelegate).not.toHaveBeenCalled();
  });

  it('AngularServices direct server group conversion throws the contextual missing transformer error', () => {
    expect(() =>
      AngularServices.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration({
        selectedProvider: provider,
      }),
    ).toThrowError(`No "serverGroup.transformer" service found for provider "${provider}"`);
  });
});

describe('AngularServices direct|load balancer data source registration', () => {
  let originalInjector: typeof ngimport.$injector;
  let originalQ: typeof ngimport.$q;

  beforeEach(() => {
    originalInjector = ngimport.$injector;
    originalQ = ngimport.$q;
    (ngimport as any).$injector = undefined;
    (ngimport as any).$q = undefined;
    (AngularServices as any).directCacheInitializer = null;
    (AngularServices as any).directLoadBalancerReader = null;
    (AngularServices as any).directSecurityGroupReader = null;
    (AngularServices as any).directServerGroupWriter = null;
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => {
    (ngimport as any).$injector = originalInjector;
    (ngimport as any).$q = originalQ;
    (AngularServices as any).directCacheInitializer = null;
    (AngularServices as any).directLoadBalancerReader = null;
    (AngularServices as any).directSecurityGroupReader = null;
    (AngularServices as any).directServerGroupWriter = null;
    ApplicationDataSourceRegistry.clearDataSources();
  });

  it('load balancer data source uses the shared reader and returns a Promise-compatible onLoad result', async () => {
    const loadBalancers = [{ name: 'app-test-detail' }] as any[];
    const loadBalancerReader = {
      loadLoadBalancers: jasmine.createSpy('loadLoadBalancers').and.returnValue(Promise.resolve(loadBalancers)),
    };
    const readerAccessor = spyOnProperty(AngularServices, 'loadBalancerReader', 'get').and.returnValue(
      loadBalancerReader as any,
    );

    registerLoadBalancerDataSource();
    const dataSource = ApplicationDataSourceRegistry.getDataSources()[0];

    await expectAsync(Promise.resolve(dataSource.loader({ name: 'app' } as any))).toBeResolvedTo(loadBalancers);
    await expectAsync(Promise.resolve(dataSource.onLoad({ name: 'app' } as any, loadBalancers))).toBeResolvedTo(
      loadBalancers,
    );
    expect(readerAccessor).toHaveBeenCalled();
    expect(loadBalancerReader.loadLoadBalancers).toHaveBeenCalledWith('app');
  });
});
