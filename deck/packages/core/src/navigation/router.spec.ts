import { HashLocationService, UIRouterReact } from '@uirouter/react';
import { RejectType, UrlService } from '@uirouter/core';
import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { ApplicationReader } from '../application/service/ApplicationReader';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { RecentHistoryService } from '../history';
import { SpinErrorBoundary } from '../presentation';
import { ProjectReader } from '../projects/service/ProjectReader';
import './coreRoutes';
import { getDirectRouter, setDirectRouter } from './directRouter';
import { stateChangeSuccess$ } from './routerContext';
import { registerRouteLifecycles } from './routeLifecycles';
import { configureRouter, startRouter } from './router';
import {
  getRootStateRegistrationsForTests,
  registerRootState,
  resetRootStateRegistrationsForTests,
} from './rootState.registration';
import { StateConfigProvider } from './state.provider';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason: unknown) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  let reject: (reason: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

async function waitForRoutingState(
  routingState: ReturnType<typeof createDeckRuntime>['routingState'],
  expected: boolean,
): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    let settled = false;
    let unsubscribe: (() => void) | undefined;
    let watchdog: ReturnType<typeof window.setTimeout> | undefined;
    const cleanup = () => {
      unsubscribe?.();
      unsubscribe = undefined;
      if (watchdog !== undefined) {
        window.clearTimeout(watchdog);
        watchdog = undefined;
      }
    };
    const settle = (callback: () => void) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      callback();
    };

    unsubscribe = routingState.subscribe((routing) => {
      if (routing === expected) {
        settle(resolve);
      }
    });
    if (settled) {
      cleanup();
      return;
    }
    watchdog = window.setTimeout(() => {
      settle(() =>
        reject(
          new Error(
            `Timed out waiting for routing state ${expected}; current routing state is ${routingState.routing}`,
          ),
        ),
      );
    }, 500);
  });
}

describe('configureRouter', () => {
  const routers: UIRouterReact[] = [];
  let originalRegistrations: ReturnType<typeof getRootStateRegistrationsForTests>;

  function createRuntimeRouter(): { router: UIRouterReact; runtime: ReturnType<typeof createDeckRuntime> } {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services, runtime.routingState);
    routers.push(router);
    return { router, runtime };
  }

  function createRouter(): UIRouterReact {
    return createRuntimeRouter().router;
  }

  beforeEach(() => {
    originalRegistrations = getRootStateRegistrationsForTests();
    resetRootStateRegistrationsForTests(originalRegistrations);
  });

  afterEach(() => {
    routers.splice(0).forEach((router) => router.dispose());
    resetRootStateRegistrationsForTests(originalRegistrations);
    setDirectRouter(null);
    window.location.hash = '';
  });

  it('registers Deck parameter types on the direct UI Router instance', () => {
    const router = createRouter();

    expect(router.urlService.config.type('boolean')).toBeDefined();
    expect(router.urlService.config.type('inverse-boolean')).toBeDefined();
    expect(router.urlService.config.type('sortKey')).toBeDefined();
    expect(router.urlService.config.type('trueKeyObject')).toBeDefined();
  });

  it('registers a home root state for direct React rendering', () => {
    const router = createRouter();

    expect(router.stateRegistry.get('home')).toBeDefined();
  });

  it('registers the critical Core state inventory directly', () => {
    const router = createRouter();
    const stateNames = router.stateRegistry.get().map((state) => state.name);

    [
      'home',
      'home.search',
      'home.apiTokens',
      'home.globalBanners',
      'home.applications',
      'home.applications.application',
      'home.applications.application.tasks',
      'home.applications.application.tasks.taskDetails',
      'home.applications.application.pipelines',
      'home.applications.application.pipelines.executions',
      'home.project',
      'home.project.application',
      'home.project.application.tasks',
      'home.project.application.pipelines',
      'home.taskLookup',
      'home.executionLookup',
      'home.instanceDetails',
      'home.firewallDetails',
    ].forEach((stateName) => expect(stateNames).toContain(stateName));
  });

  it('keeps the first root state registration when providers register the same state name', () => {
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'providerRoute', url: '/first' }));
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'providerRoute', url: '/second' }));

    const router = createRouter();

    expect(router.stateRegistry.get('home.providerRoute').url.toString()).toBe('/first');
  });

  it('applies root state registrations to the direct UI Router state registry', () => {
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'registeredRoot', url: '/registered-root' }));

    const router = createRouter();

    expect(router.stateRegistry.get('home.registeredRoot')).toBeDefined();
  });

  it('rewrites fallback, trailing slash, and Core legacy URLs through the URL service', () => {
    const router = createRouter();

    [
      ['/not-a-route', '/'],
      ['/search/', '/search'],
      ['/', '/search'],
      ['/infrastructure?q=payments', '/search?q=payments'],
      ['/projects/delivery', '/projects/delivery/dashboard'],
      ['/applications/payments/securityGroups', '/applications/payments/firewalls'],
      [
        '/applications/payments/securityGroupDetails/aws/account/us-east-1/vpc-1/firewall',
        '/applications/payments/firewallDetails/aws/account/us-east-1/vpc-1/firewall',
      ],
    ].forEach(([source, target]) => {
      router.urlService.url(source);
      router.urlService.sync();

      expect(router.urlService.url()).toBe(target);
    });
  });

  it('uses hash URLs to preserve existing Deck routes', () => {
    const router = createRouter();

    expect(router.locationService).toEqual(jasmine.any(HashLocationService));
  });

  it('configures the direct router without starting URL handling', () => {
    const listen = spyOn(UrlService.prototype, 'listen');
    const sync = spyOn(UrlService.prototype, 'sync');

    const router = createRouter();

    expect(getDirectRouter()).toBe(router);
    expect(listen.calls.allArgs().filter((args) => args.length === 0).length).toBe(0);
    expect(sync).not.toHaveBeenCalled();
  });

  it('does not register infrastructure data sources owned by runtime initialization', () => {
    const originalDataSources = ApplicationDataSourceRegistry.getDataSources();
    ApplicationDataSourceRegistry.clearDataSources();
    const registerDataSource = spyOn(ApplicationDataSourceRegistry, 'registerDataSource').and.callThrough();

    try {
      createRouter();

      const registeredKeys = registerDataSource.calls.allArgs().map(([config]) => config.key);
      expect(registeredKeys).not.toContain('serverGroups');
      expect(registeredKeys).not.toContain('loadBalancers');
      expect(registeredKeys).not.toContain('securityGroups');
    } finally {
      ApplicationDataSourceRegistry.clearDataSources();
      originalDataSources.forEach((dataSource) => ApplicationDataSourceRegistry.registerDataSource(dataSource));
    }
  });

  it('starts URL listening before synchronizing the initial location', () => {
    const order: string[] = [];
    spyOn(UrlService.prototype, 'listen').and.callFake(() => {
      order.push('listen');
      return undefined;
    });
    spyOn(UrlService.prototype, 'sync').and.callFake(() => {
      order.push('sync');
      return undefined;
    });
    const router = createRouter();

    startRouter(router);
    startRouter(router);

    expect(order).toEqual(['listen', 'sync']);
    expect(router.started).toBe(true);
  });

  it('matches the initial hash URL during direct router startup', async () => {
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'registeredRoot', url: '/registered-root' }));
    window.location.hash = '#/registered-root';

    const router = createRouter();
    startRouter(router);
    await router.globals.transition?.promise;

    expect(router.stateService.current.name).toBe('home.registeredRoot');
  });

  it('provides state change events without an Angular injector', async () => {
    window.location.hash = '';
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'registeredRoot', url: '/registered-root' }));

    const router = createRouter();
    const stateChanges: string[] = [];
    const subscription = stateChangeSuccess$(router).subscribe(({ to }) => stateChanges.push(to.name));

    try {
      await router.stateService.go('home.registeredRoot');

      expect(stateChanges).toContain('home.registeredRoot');
    } finally {
      subscription.unsubscribe();
    }
  });

  it('transitions through both application trees and resolves their applications', async () => {
    const projectConfiguration = { name: 'delivery' } as any;
    const getApplication = spyOn(ApplicationReader, 'getApplication').and.callFake((name: string) =>
      Promise.resolve({ name, dataSources: [] } as any),
    );
    spyOn(ProjectReader, 'getProjectConfig').and.resolveTo(projectConfiguration);
    const router = createRouter();

    await router.stateService.go(
      'home.applications.application.tasks',
      { application: 'payments' },
      { location: false },
    );
    expect(router.stateService.current.name).toBe('home.applications.application.tasks');

    await router.stateService.go(
      'home.project.application.tasks',
      {
        application: 'transfers',
        project: 'delivery',
      },
      { location: false },
    );
    expect(router.stateService.current.name).toBe('home.project.application.tasks');
    const projectTransition = router.globals.successfulTransitions.peekTail();
    expect(getApplication).toHaveBeenCalledWith('payments', false);
    expect(getApplication).toHaveBeenCalledWith('transfers', false);
    expect(ProjectReader.getProjectConfig).toHaveBeenCalledOnceWith('delivery');
    expect(projectTransition.injector().get('projectConfiguration')).toBe(projectConfiguration);
  });

  it('updates page titles and recent history after successful direct transitions', async () => {
    const addHistory = spyOn(RecentHistoryService, 'addItem');
    const router = createRouter();

    await router.stateService.go('home.instanceDetails', {
      account: 'prod',
      instanceId: 'i-123',
      provider: 'aws',
      region: 'eu-west-1',
    });

    expect(document.title).toBe('Spinnaker · Instance Details: i-123');
    expect(addHistory).toHaveBeenCalledOnceWith(
      'instances',
      'home.instanceDetails',
      jasmine.objectContaining({ instanceId: 'i-123', provider: 'aws' }),
      undefined,
    );
  });

  it('shows a loading title while a direct transition is pending', async () => {
    const { router, runtime } = createRuntimeRouter();
    const pending = deferred<void>();
    router.stateRegistry.register({
      name: 'route-lifecycle-pending',
      resolve: [{ token: 'pending', resolveFn: () => pending.promise }],
    });
    document.title = 'Before pending transition';

    const settlement = router.stateService.go('route-lifecycle-pending');
    await waitForRoutingState(runtime.routingState, true);

    expect(document.title).toBe('Spinnaker: Loading...');
    pending.resolve(undefined);
    await settlement;
  });

  it('settles routing without success side effects when a direct transition rejects', async () => {
    const { router, runtime } = createRuntimeRouter();
    const handleRoutingStart = spyOn(runtime.services.pageTitleService, 'handleRoutingStart').and.callThrough();
    const handleRoutingError = spyOn(runtime.services.pageTitleService, 'handleRoutingError').and.callThrough();
    const handleRoutingSuccess = spyOn(runtime.services.pageTitleService, 'handleRoutingSuccess').and.callThrough();
    const addHistory = spyOn(RecentHistoryService, 'addItem');
    const routingValues: boolean[] = [];
    runtime.routingState.subscribe((routing) => routingValues.push(routing));
    router.stateRegistry.register({
      name: 'route-lifecycle-rejected',
      data: {
        pageTitleMain: { label: 'Rejected transition' },
        history: { type: 'route-lifecycle-rejected' },
      },
      resolve: [{ token: 'failure', resolveFn: () => Promise.reject(new Error('transition failed')) }],
    });
    document.title = 'Before rejection';

    const settlement = router.stateService.go('route-lifecycle-rejected').then(
      () => 'resolved',
      () => 'rejected',
    );

    expect(await settlement).toBe('rejected');
    await Promise.resolve();

    expect(runtime.routingState.routing).toBe(false);
    expect(routingValues).toEqual([false, true, false]);
    expect(document.title).toBe('Spinnaker: Error');
    expect(handleRoutingStart).toHaveBeenCalledTimes(1);
    expect(handleRoutingError).toHaveBeenCalledTimes(1);
    expect(handleRoutingSuccess).not.toHaveBeenCalled();
    expect(addHistory).not.toHaveBeenCalled();
  });

  it('restores the prior title when a direct transition aborts', async () => {
    const { router } = createRuntimeRouter();
    router.stateRegistry.register({ name: 'route-lifecycle-aborted' });
    const deregisterAbort = router.transitionService.onStart({ to: 'route-lifecycle-aborted' }, () => false, {
      priority: -1,
    });
    document.title = 'Before abort';

    const settlement = router.stateService.go('route-lifecycle-aborted').then(
      () => ({ status: 'resolved', type: undefined }),
      (error) => ({ status: 'rejected', type: error.type }),
    );

    expect(await settlement).toEqual({ status: 'rejected', type: RejectType.ABORTED });
    expect(document.title).toBe('Before abort');
    deregisterAbort();
  });

  it('settles superseded routing and applies success side effects only to the replacement', async () => {
    const { router, runtime } = createRuntimeRouter();
    const handleRoutingSuccess = spyOn(runtime.services.pageTitleService, 'handleRoutingSuccess').and.callThrough();
    const addHistory = spyOn(RecentHistoryService, 'addItem');
    const routingValues: boolean[] = [];
    runtime.routingState.subscribe((routing) => routingValues.push(routing));
    router.stateRegistry.register({
      name: 'route-lifecycle-superseded',
      data: {
        pageTitleMain: { label: 'Superseded transition' },
        history: { type: 'route-lifecycle-superseded' },
      },
    });
    router.stateRegistry.register({
      name: 'route-lifecycle-replacement',
      data: {
        pageTitleMain: { label: 'Replacement transition' },
        history: { type: 'route-lifecycle-replacement' },
      },
    });
    document.title = 'Before supersession';

    let replacement: ReturnType<typeof router.stateService.go> | undefined;
    let supersedingHookCalls = 0;
    const deregisterSupersedingHook = router.transitionService.onStart(
      { to: 'route-lifecycle-superseded' },
      () => {
        supersedingHookCalls++;
        replacement = router.stateService.go('route-lifecycle-replacement');
      },
      { priority: -1 },
    );
    const supersededSettlement = router.stateService.go('route-lifecycle-superseded').then(
      () => ({ status: 'resolved', type: undefined }),
      (error) => ({ status: 'rejected', type: error.type }),
    );

    expect(await supersededSettlement).toEqual({ status: 'rejected', type: RejectType.SUPERSEDED });
    expect(supersedingHookCalls).toBe(1);
    expect(replacement).toBeDefined();
    await replacement;
    deregisterSupersedingHook();
    await waitForRoutingState(runtime.routingState, false);

    expect(runtime.routingState.routing).toBe(false);
    expect(routingValues).toEqual([false, true, false]);
    expect(document.title).toBe('Replacement transition');
    expect(handleRoutingSuccess).toHaveBeenCalledTimes(1);
    expect(addHistory).toHaveBeenCalledTimes(1);
    expect(addHistory.calls.mostRecent().args[1]).toBe('route-lifecycle-replacement');
  });

  it('does not publish or run success side effects when a transition settles after runtime disposal', async () => {
    const { router, runtime } = createRuntimeRouter();
    const pending = deferred<void>();
    const handleRoutingSuccess = spyOn(runtime.services.pageTitleService, 'handleRoutingSuccess').and.callThrough();
    const addHistory = spyOn(RecentHistoryService, 'addItem');
    const routingValues: boolean[] = [];
    runtime.routingState.subscribe((routing) => routingValues.push(routing));
    router.stateRegistry.register({
      name: 'route-lifecycle-disposed',
      data: {
        pageTitleMain: { label: 'Disposed transition' },
        history: { type: 'route-lifecycle-disposed' },
      },
      resolve: [{ token: 'pending', resolveFn: () => pending.promise }],
    });
    document.title = 'Before disposal';

    const settlement = router.stateService.go('route-lifecycle-disposed').then(
      () => 'resolved',
      () => 'rejected',
    );
    await waitForRoutingState(runtime.routingState, true);
    expect(runtime.routingState.routing).toBe(true);

    runtime.dispose();
    const valuesAfterDisposal = routingValues.slice();
    pending.reject(new Error('late transition failure'));
    expect(await settlement).toBe('rejected');
    await Promise.resolve();

    expect(runtime.routingState.routing).toBe(false);
    expect(valuesAfterDisposal).toEqual([false, true, false]);
    expect(routingValues).toEqual(valuesAfterDisposal);
    expect(document.title).toBe('Spinnaker: Loading...');
    expect(handleRoutingSuccess).not.toHaveBeenCalled();
    expect(addHistory).not.toHaveBeenCalled();
  });

  it('wraps every critical direct React route view in a route-level error boundary', () => {
    const router = createRouter();

    [
      'home.search',
      'home.applications',
      'home.applications.application',
      'home.applications.application.tasks',
      'home.applications.application.pipelines',
      'home.applications.application.pipelines.executions',
      'home.project',
      'home.project.application',
      'home.project.application.tasks',
      'home.project.application.pipelines',
      'home.instanceDetails',
      'home.firewallDetails',
    ].forEach((stateName) => {
      const state = router.stateRegistry.get(stateName);
      const directReactViews = Object.values(state.views ?? {}).filter((view) => view.$type === 'react');

      expect(directReactViews.length).withContext(stateName).toBeGreaterThan(0);
      directReactViews.forEach((view) => {
        const wrapper = shallow(React.createElement(view.component));

        expect(wrapper.type()).withContext(stateName).toBe(SpinErrorBoundary);
        expect(wrapper.prop('category')).withContext(stateName).toBe(stateName);
      });
    });
  });

  it('cleans up direct route hooks and configures a second router in the same process', async () => {
    const lifecycleRouter = new UIRouterReact();
    routers.push(lifecycleRouter);
    const lifecycleRuntime = createDeckRuntime(lifecycleRouter);
    lifecycleRouter.disposable(lifecycleRuntime);
    const initialStartHooks = lifecycleRouter.transitionService.getHooks('onStart').length;
    const initialSuccessHooks = lifecycleRouter.transitionService.getHooks('onSuccess').length;
    const deregisterLifecycles = registerRouteLifecycles(
      lifecycleRouter,
      lifecycleRuntime.services.pageTitleService,
      lifecycleRuntime.routingState,
    );
    expect(lifecycleRouter.transitionService.getHooks('onStart').length).toBe(initialStartHooks + 1);
    expect(lifecycleRouter.transitionService.getHooks('onSuccess').length).toBe(initialSuccessHooks + 1);
    deregisterLifecycles();
    deregisterLifecycles();
    expect(lifecycleRouter.transitionService.getHooks('onStart').length).toBe(initialStartHooks);
    expect(lifecycleRouter.transitionService.getHooks('onSuccess').length).toBe(initialSuccessHooks);

    const firstRouter = createRouter();
    const firstStartHooks = firstRouter.transitionService.getHooks('onStart').length;
    const firstSuccessHooks = firstRouter.transitionService.getHooks('onSuccess').length;

    expect(firstStartHooks).toBeGreaterThan(0);
    expect(firstSuccessHooks).toBeGreaterThan(0);
    firstRouter.dispose();

    const secondRouter = createRouter();
    expect(secondRouter.transitionService.getHooks('onStart').length).toBe(firstStartHooks);
    expect(secondRouter.transitionService.getHooks('onSuccess').length).toBe(firstSuccessHooks);
    await secondRouter.stateService.go('home.search');
    expect(secondRouter.stateService.current.name).toBe('home.search');
  });

  it('disposes a failed configuration without publishing or starting its router', () => {
    const previousRouter = new UIRouterReact();
    routers.push(previousRouter);
    setDirectRouter(previousRouter);
    const failure = new Error('state configuration failed');
    spyOn(StateConfigProvider.prototype, 'setStates').and.throwError(failure);
    const dispose = spyOn(UIRouterReact.prototype, 'dispose').and.callThrough();
    const listen = spyOn(UrlService.prototype, 'listen');
    const sync = spyOn(UrlService.prototype, 'sync');

    const failedRouter = new UIRouterReact();
    const runtime = createDeckRuntime(failedRouter);
    failedRouter.disposable(runtime);
    expect(() => configureRouter(failedRouter, runtime.services, runtime.routingState)).toThrow(failure);

    expect(getDirectRouter()).toBe(previousRouter);
    const ownershipDisposals = dispose.calls.all().filter(({ args }) => args.length === 0);
    expect(ownershipDisposals.length).toBe(1);
    expect(ownershipDisposals[0].object).not.toBe(previousRouter);
    expect(listen.calls.allArgs().filter((args) => args.length === 0).length).toBe(0);
    expect(sync).not.toHaveBeenCalled();
  });
});
