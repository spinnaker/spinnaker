import { UIRouterReact } from '@uirouter/react';

import { AccountService } from '../account';
import { mockHttpClient } from '../api/mock/jasmine';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { InfrastructureCaches } from '../cache';
import { CloudProviderRegistry } from '../cloudProvider';
import { SETTINGS } from '../config';
import { registerLoadBalancerDataSource } from '../loadBalancer/loadBalancer.dataSource';
import { createLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { AngularServices } from './services';

describe('Deck runtime services', () => {
  const provider = 'serverGroupCommandBuilderTest';
  let runtime: DeckRuntime;

  beforeEach(() => {
    runtime = createDeckRuntime(new UIRouterReact());
  });

  afterEach(() => {
    runtime.dispose();
    InfrastructureCaches.destroyCaches();
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
  });

  it('returns a direct infrastructure search service when Angular is not bootstrapped', () => {
    expect(runtime.services.infrastructureSearchService.getSearcher()).toBeDefined();
  });

  it('returns a direct page title service when Angular is not bootstrapped', () => {
    runtime.services.pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Search' } });

    expect(document.title).toBe('Search');
  });

  it('returns direct modal and cache fallbacks when Angular is not bootstrapped', async () => {
    spyOn(InfrastructureCaches, 'createCache').and.returnValue({} as any);
    spyOn(InfrastructureCaches, 'clearCache');
    spyOn(runtime.services.securityGroupReader, 'getAllSecurityGroups').and.returnValue(Promise.resolve([]));

    expect(AngularServices.modalService.open).toBeDefined();
    expect(() => AngularServices.modalStackService.dismissAll()).not.toThrow();
    await expectAsync(runtime.services.cacheInitializer.refreshCaches()).toBeResolved();
  });

  it('returns direct root scope and timeout fallbacks when Angular is not bootstrapped', async () => {
    const applied = jasmine.createSpy('applied');
    const timedOut = jasmine.createSpy('timedOut');

    AngularServices.$rootScope.$apply(applied);
    const timeout = AngularServices.$timeout(timedOut, 1000);
    const settlement = expectAsync(timeout as any).toBeRejectedWith('canceled');
    expect(AngularServices.$timeout.cancel(timeout)).toBe(true);

    expect(applied).toHaveBeenCalled();
    await settlement;
    expect(timedOut).not.toHaveBeenCalled();
  });

  it('returns a direct execution details section service when Angular is not bootstrapped', () => {
    expect(() => runtime.services.executionDetailsSectionService.synchronizeSection(['stage'])).not.toThrow();
  });

  it('returns a direct $q fallback with Angular-style promise helpers', async () => {
    const directQ = AngularServices.$q as any;

    await expectAsync(directQ.when('when')).toBeResolvedTo('when');
    await expectAsync(directQ.resolve('resolve')).toBeResolvedTo('resolve');
    await expectAsync(directQ.all([Promise.resolve('all')])).toBeResolvedTo(['all']);
    await expectAsync(directQ((resolve: (value: string) => void) => resolve('callable'))).toBeResolvedTo('callable');

    const deferred = directQ.defer();
    deferred.resolve('deferred');

    await expectAsync(deferred.promise).toBeResolvedTo('deferred');
    await expectAsync(directQ.reject('reject')).toBeRejectedWith('reject');
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
    const command = await runtime.services.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(
      provider,
      stage,
      pipeline,
    );

    expect(command).toEqual({ currentStage: stage, currentPipeline: pipeline });
  });

  it('converts server group commands through the direct provider transformer without Angular DI', () => {
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
    expect(runtime.services.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)).toEqual({
      converted: true,
      base: command,
    });
  });

  it('returns a direct security group reader when Angular is not bootstrapped', () => {
    expect(runtime.services.securityGroupReader.getAllSecurityGroups).toEqual(jasmine.any(Function));
  });

  it('returns a direct instance type service when Angular is not bootstrapped', () => {
    expect(runtime.services.instanceTypeService.getCategoryForMultipleInstanceTypes).toEqual(jasmine.any(Function));
  });
});

describe('Deck runtime service accessors', () => {
  const provider = 'directServiceAccessorTest';
  let runtime: DeckRuntime;

  beforeEach(() => {
    runtime = createDeckRuntime(new UIRouterReact());
  });

  afterEach(() => {
    runtime.dispose();
    InfrastructureCaches.destroyCaches();
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
  });

  it('lazily caches service instances', () => {
    expect(runtime.services.serverGroupWriter).toBe(runtime.services.serverGroupWriter);
    expect(runtime.services.cacheInitializer).toBe(runtime.services.cacheInitializer);
    expect(runtime.services.loadBalancerReader).toBe(runtime.services.loadBalancerReader);
  });

  it('exposes working cache initializer methods', async () => {
    spyOn(InfrastructureCaches, 'createCache').and.returnValue({} as any);
    spyOn(InfrastructureCaches, 'clearCache');
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve([]));
    const getAllSecurityGroups = spyOn(runtime.services.securityGroupReader, 'getAllSecurityGroups').and.returnValue(
      Promise.resolve([]),
    );
    const cacheInitializer = runtime.services.cacheInitializer;

    expect(cacheInitializer.initialize).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCache).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCaches).toEqual(jasmine.any(Function));

    await cacheInitializer.initialize();
    await cacheInitializer.refreshCache('securityGroups');
    await cacheInitializer.refreshCaches();

    expect(AccountService.listProviders).toHaveBeenCalledWith();
    expect(getAllSecurityGroups).toHaveBeenCalledTimes(3);
  });

  it('submits destroy jobs through the server group writer', async () => {
    const serverGroupWriter = runtime.services.serverGroupWriter;
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

  it('uses a registered provider transformer in the load balancer reader', async () => {
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
    const loadBalancerReader = runtime.services.loadBalancerReader;
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

  it('preserves provider and set normalization in the load balancer transformer factory', async () => {
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

  it('resolves each provider set transformer once in the load balancer transformer', () => {
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

  it('leaves unregistered providers unchanged in the load balancer transformer', async () => {
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.returnValue(false),
      getDelegate: jasmine.createSpy('getDelegate'),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate as any);
    const loadBalancer = { name: 'app-test-detail', provider };

    await expectAsync(transformer.normalizeLoadBalancer(loadBalancer)).toBeResolvedTo(loadBalancer);
    expect(providerServiceDelegate.getDelegate).not.toHaveBeenCalled();
  });

  it('throws the contextual missing server group transformer error', () => {
    expect(() =>
      runtime.services.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration({
        selectedProvider: provider,
      }),
    ).toThrowError(`No "serverGroup.transformer" service found for provider "${provider}"`);
  });
});

describe('Deck runtime load balancer data source registration', () => {
  let runtime: DeckRuntime;

  beforeEach(() => {
    runtime = createDeckRuntime(new UIRouterReact());
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => {
    runtime.dispose();
    ApplicationDataSourceRegistry.clearDataSources();
  });

  it('load balancer data source uses the shared reader and returns a Promise-compatible onLoad result', async () => {
    const loadBalancers = [{ name: 'app-test-detail' }] as any[];
    const loadBalancerReader = {
      loadLoadBalancers: jasmine.createSpy('loadLoadBalancers').and.returnValue(Promise.resolve(loadBalancers)),
    };
    registerLoadBalancerDataSource(runtime.promiseService, loadBalancerReader as any);
    const dataSource = ApplicationDataSourceRegistry.getDataSources()[0];

    await expectAsync(Promise.resolve(dataSource.loader({ name: 'app' } as any))).toBeResolvedTo(loadBalancers);
    await expectAsync(Promise.resolve(dataSource.onLoad({ name: 'app' } as any, loadBalancers))).toBeResolvedTo(
      loadBalancers,
    );
    expect(loadBalancerReader.loadLoadBalancers).toHaveBeenCalledWith('app');
  });
});
