import { UrlService } from '@uirouter/core';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import ReactDOM from 'react-dom';

import { bootstrapDeck, createDeckRoot, resetBootstrapDeckForTests } from './bootstrapDeck';
import { AngularServices } from '../angular/services';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { ApplicationReader } from '../application/service/ApplicationReader';
import type { IHttpClientImplementation } from '../api';
import { RequestBuilder } from '../api';
import { AuthenticationService } from '../authentication';
import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { resetAuthenticationRuntime } from '../authentication/authentication.module';
import { GlobalBannerService } from '../banner/global/GlobalBannerService';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import { SETTINGS } from '../config/settings';
import { getDirectRouter, setDirectRouter } from '../navigation/directRouter';
import { configureRouter } from '../navigation/router';
import { NotificationService } from '../notification/NotificationService';
import { PluginRegistry } from '../plugins/plugin.registry';
import { resetPluginInitializationForTests } from '../plugins/plugin.module';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';
import type { DeckRuntime } from './DeckRuntime';
import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import { SpinnakerContainer } from './SpinnakerContainer';
import * as State from '../state';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

interface IStateSnapshot {
  state: object;
  descriptors: PropertyDescriptorMap;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('bootstrapDeck', () => {
  const actualRegisterDataSource = ApplicationDataSourceRegistry.registerDataSource;
  const actualRender = ReactDOM.render;
  const actualRouterPlugin = UIRouterReact.prototype.plugin;
  const configuredRouters: UIRouterReact[] = [];
  const roots: HTMLElement[] = [];
  const stateSingletons = [
    State.ClusterState,
    State.ExecutionState,
    State.FunctionState,
    State.LoadBalancerState,
    State.SecurityGroupState,
  ];
  let authenticationSpy: jasmine.Spy;
  let cacheInitializeSpy: jasmine.Spy;
  let deckManifestSpy: jasmine.Spy;
  let listenSpy: jasmine.Spy;
  let metadataGet: jasmine.Spy;
  let notificationMetadataSpy: jasmine.Spy;
  let originalAuthEnabled: boolean;
  let originalCheckForUpdates: boolean;
  let originalDataSources: ReturnType<typeof ApplicationDataSourceRegistry.getDataSources>;
  let originalHash: string;
  let originalHttpClient: IHttpClientImplementation;
  let pluginLoadsSpy: jasmine.Spy;
  let renderSpy: jasmine.Spy;
  let routerPluginSpy: jasmine.Spy;
  let runtimeDataSourceSpy: jasmine.Spy;
  let schedulerCreateSpy: jasmine.Spy;
  let stateSnapshots: IStateSnapshot[];
  let syncSpy: jasmine.Spy;
  let unmountSpy: jasmine.Spy;

  function createRoot(attachToBody = false): HTMLElement {
    const root = document.createElement('div');
    roots.push(root);
    if (attachToBody) {
      document.body.appendChild(root);
    }
    return root;
  }

  function createConfiguredRouter(): UIRouterReact {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services);
    configuredRouters.push(router);
    return router;
  }

  function versionSchedulerCount(): number {
    return schedulerCreateSpy.calls.allArgs().filter((args) => args.length === 0).length;
  }

  beforeEach(() => {
    resetBootstrapDeckForTests();
    originalAuthEnabled = SETTINGS.authEnabled;
    originalCheckForUpdates = SETTINGS.checkForUpdates;
    originalDataSources = ApplicationDataSourceRegistry.getDataSources();
    originalHash = window.location.hash;
    originalHttpClient = RequestBuilder.defaultHttpClient;
    stateSnapshots = stateSingletons.map((state) => ({ state, descriptors: Object.getOwnPropertyDescriptors(state) }));
    ApplicationDataSourceRegistry.clearDataSources();
    SETTINGS.authEnabled = true;
    SETTINGS.checkForUpdates = true;
    metadataGet = jasmine.createSpy('metadataGet').and.returnValue(Promise.resolve([]));
    RequestBuilder.defaultHttpClient = { get: metadataGet } as IHttpClientImplementation;
    authenticationSpy = spyOn(AuthenticationInitializer, 'authenticateUser').and.returnValue(Promise.resolve(true));
    spyOn(GlobalBannerService, 'getActiveBanners').and.returnValue(Promise.resolve([]));
    notificationMetadataSpy = spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([]),
    );
    deckManifestSpy = spyOn(PluginRegistry.prototype, 'loadPluginManifestFromDeck').and.returnValue(
      Promise.resolve([]),
    );
    spyOn(PluginRegistry.prototype, 'loadPluginManifestFromGate').and.returnValue(Promise.resolve([]));
    pluginLoadsSpy = spyOn(PluginRegistry.prototype, 'loadPlugins').and.returnValue(Promise.resolve([]));
    runtimeDataSourceSpy = spyOn(ApplicationDataSourceRegistry, 'registerDataSource').and.callThrough();
    cacheInitializeSpy = spyOn(CacheInitializerService.prototype, 'initialize').and.returnValue(Promise.resolve([]));
    renderSpy = spyOn(ReactDOM, 'render').and.callThrough();
    unmountSpy = spyOn(ReactDOM, 'unmountComponentAtNode').and.callThrough();
    schedulerCreateSpy = spyOn(SchedulerFactory, 'createScheduler').and.callThrough();
    routerPluginSpy = spyOn(UIRouterReact.prototype, 'plugin').and.callThrough();
    listenSpy = spyOn(UrlService.prototype, 'listen');
    syncSpy = spyOn(UrlService.prototype, 'sync');
  });

  afterEach(() => {
    resetBootstrapDeckForTests();
    configuredRouters.splice(0).forEach((router) => router.dispose());
    roots.forEach((root) => {
      if (root.hasChildNodes()) {
        ReactDOM.unmountComponentAtNode(root);
      }
      root.remove();
    });
    roots.length = 0;
    document.querySelectorAll('.loading-placeholder').forEach((placeholder) => placeholder.remove());
    stateSnapshots.forEach(({ state, descriptors }) => {
      Object.keys(state).forEach((key) => delete (state as any)[key]);
      Object.defineProperties(state, descriptors);
    });
    ApplicationDataSourceRegistry.clearDataSources();
    originalDataSources.forEach((dataSource) => ApplicationDataSourceRegistry.registerDataSource(dataSource));
    setDirectRouter(null);
    resetAuthenticationRuntime();
    AuthenticationService.reset();
    resetPluginInitializationForTests();
    SETTINGS.authEnabled = originalAuthEnabled;
    SETTINGS.checkForUpdates = originalCheckForUpdates;
    RequestBuilder.defaultHttpClient = originalHttpClient;
    window.location.hash = originalHash;
  });

  it('rejects with a clear error when the root element is missing', async () => {
    await expectAsync(bootstrapDeck(null)).toBeRejectedWithError(
      'Cannot bootstrap Deck: #spinnaker-root was not found',
    );
  });

  it('renders Deck into the provided root element and returns a promise', async () => {
    const root = createRoot();

    const result = bootstrapDeck(root);

    expect(result).toEqual(jasmine.any(Promise));
    await result;
    expect(renderSpy).toHaveBeenCalledWith(jasmine.any(Object), root);
  });

  it('allows the direct React root to fill the viewport height', async () => {
    const root = createRoot(true);
    root.id = 'spinnaker-root';

    await bootstrapDeck(root);

    expect(window.getComputedStyle(root).height).toBe(`${window.innerHeight}px`);
  });

  it('loads shared flex layout utilities used by direct React views', () => {
    const element = document.createElement('div');
    element.className = 'flex-container-h';
    document.body.appendChild(element);

    expect(window.getComputedStyle(element).display).toBe('flex');

    element.remove();
  });

  it('loads the full presentation stylesheet set used by direct React views', () => {
    const detailsPanel = document.createElement('div');
    detailsPanel.className = 'details-panel';
    const navPopover = document.createElement('a');
    navPopover.className = 'nav-popover';
    document.body.appendChild(detailsPanel);
    document.body.appendChild(navPopover);

    expect(window.getComputedStyle(detailsPanel).display).toBe('flex');
    expect(window.getComputedStyle(navPopover).fontWeight).toBe('600');

    detailsPanel.remove();
    navPopover.remove();
  });

  it('creates an explicit scroll container for the direct main view without rewriting routed children', () => {
    const host = createRoot(true);
    const router = createConfiguredRouter();
    const runtime = createDeckRuntime();
    const wrapper = mount(createDeckRoot(router, runtime), { attachTo: host });

    const mainView = host.querySelector('.spinnaker-main-view') as HTMLElement;
    expect(mainView).not.toBeNull();
    const routedChild = document.createElement('section');
    routedChild.setAttribute('name', 'main');
    mainView.appendChild(routedChild);
    expect(window.getComputedStyle(mainView).overflowY).toBe('auto');
    expect(window.getComputedStyle(mainView).minHeight).toBe('0px');
    expect(window.getComputedStyle(routedChild).display).toBe('block');
    expect(window.getComputedStyle(routedChild).overflowY).toBe('visible');

    wrapper.unmount();
    runtime.dispose();
  });

  it('removes the static loading placeholder only after rendering', async () => {
    const root = createRoot();
    const placeholder = document.createElement('div');
    placeholder.className = 'loading-placeholder';
    document.body.appendChild(placeholder);
    renderSpy.and.callFake(() => {
      expect(document.querySelector('.loading-placeholder')).toBe(placeholder);
      return null;
    });

    await bootstrapDeck(root);

    expect(document.querySelector('.loading-placeholder')).toBeNull();
  });

  it('creates a pure direct UI Router and Spinnaker container tree for the supplied router', () => {
    const router = createConfiguredRouter();
    const runtime = createDeckRuntime();
    routerPluginSpy.calls.reset();
    (State.ExecutionState as any).filterModel = null;

    const app = createDeckRoot(router, runtime);
    const routerProvider = app.props.children;

    expect(app.type).toBe(DeckRuntimeContext.Provider);
    expect(app.props.value).toBe(runtime);
    expect(routerProvider.type).toBe(UIRouterContext.Provider);
    expect(routerProvider.props.value).toBe(router);
    expect(routerProvider.props.children.type).toBe(SpinnakerContainer);
    expect(routerProvider.props.children.props).toEqual(
      jasmine.objectContaining({ authenticating: false, routing: false }),
    );
    expect(routerPluginSpy).not.toHaveBeenCalled();
    expect(State.ExecutionState.filterModel).toBeNull();

    runtime.dispose();
  });

  it('does not use the AngularJS React hybrid router', () => {
    const router = createConfiguredRouter();
    const runtime = createDeckRuntime();

    expect(createDeckRoot(router, runtime).props.children.type).toBe(UIRouterContext.Provider);

    runtime.dispose();
  });

  it('mounts React before removing the placeholder and starts URL handling exactly once', async () => {
    const order: string[] = [];
    const root = createRoot(true);
    const placeholder = document.createElement('div');
    placeholder.className = 'loading-placeholder';
    document.body.appendChild(placeholder);

    class MountObserver extends React.Component {
      public componentDidMount(): void {
        expect(document.querySelector('.loading-placeholder')).toBe(placeholder);
        order.push('mounted');
      }

      public render(): React.ReactNode {
        return this.props.children;
      }
    }

    renderSpy.and.callFake((element: React.ReactElement, container: Element) =>
      actualRender(<MountObserver>{element}</MountObserver>, container),
    );
    listenSpy.and.callFake(() => {
      expect(document.querySelector('.loading-placeholder')).toBeNull();
      order.push('listen');
    });
    syncSpy.and.callFake(() => order.push('sync'));

    await bootstrapDeck(root);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(order).toEqual(['mounted', 'listen', 'sync']);
    expect(listenSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('hydrates cluster filter permalinks during direct bootstrap without an Angular injector', async () => {
    const root = createRoot();
    const clusterKey = 'main-euc1-se-main01:deployment payments';
    window.location.hash = `#/applications/payments/clusters?clusters=${encodeURIComponent(
      clusterKey,
    )}&acct=main-euc1-se-main01&q=payments`;
    listenSpy.and.callThrough();
    syncSpy.and.callThrough();
    renderSpy.and.returnValue(null);
    spyOn(ApplicationReader, 'getApplication').and.returnValue(
      Promise.resolve({ name: 'payments', dataSources: [] } as any),
    );

    try {
      await bootstrapDeck(root);
      const router = getDirectRouter();
      await router?.globals.transition?.promise;

      expect(router?.stateService.current.name).toBe('home.applications.application.insight.clusters');
      expect(State.ClusterState.filterModel.asFilterModel.sortFilter.clusters).toEqual({ [clusterKey]: true });
      expect(State.ClusterState.filterModel.asFilterModel.sortFilter.account).toEqual({
        'main-euc1-se-main01': true,
      });
      expect(State.ClusterState.filterModel.asFilterModel.sortFilter.filter).toBe('payments');
    } finally {
      resetBootstrapDeckForTests();
    }
  });

  it('runs startup in the required observable order', async () => {
    const order: string[] = [];
    const root = createRoot();
    let runtimeMetadataObserved = false;
    let routerObserved = false;
    let filterModel: unknown;
    runtimeDataSourceSpy.and.callFake((config: any) => {
      if (!runtimeMetadataObserved) {
        runtimeMetadataObserved = true;
        order.push('runtime metadata');
      }
      actualRegisterDataSource.call(ApplicationDataSourceRegistry, config);
    });
    authenticationSpy.and.callFake(() => {
      order.push('authentication');
      return Promise.resolve(true);
    });
    deckManifestSpy.and.callFake(() => {
      order.push('plugins');
      return Promise.resolve([]);
    });
    routerPluginSpy.and.callFake(function (...args: any[]) {
      if (!routerObserved) {
        routerObserved = true;
        order.push('router constructed');
      }
      return actualRouterPlugin.apply(this, args);
    });
    Object.defineProperty(State.ExecutionState, 'filterModel', {
      configurable: true,
      get: () => filterModel,
      set: (value) => {
        filterModel = value;
        order.push('state initialized');
      },
    });
    cacheInitializeSpy.and.callFake(() => {
      order.push('cache started');
      return Promise.resolve([]);
    });
    renderSpy.and.callFake(() => {
      order.push('render');
      return null;
    });
    listenSpy.and.callFake(() => order.push('listen'));
    syncSpy.and.callFake(() => order.push('sync'));

    await bootstrapDeck(root);

    expect(order.filter((step) => step !== 'state initialized')).toEqual([
      'authentication',
      'plugins',
      'runtime metadata',
      'router constructed',
      'cache started',
      'render',
      'listen',
      'sync',
    ]);
    expect(order).toEqual([
      'authentication',
      'plugins',
      'runtime metadata',
      'router constructed',
      'state initialized',
      'cache started',
      'render',
      'listen',
      'sync',
    ]);
    expect(notificationMetadataSpy).toHaveBeenCalledTimes(1);
  });

  it('stops after authentication fails without starting authenticated work', async () => {
    const root = createRoot();
    const originalFilterModel = State.ExecutionState.filterModel;
    authenticationSpy.and.returnValue(Promise.resolve(false));

    await bootstrapDeck(root);

    expect(runtimeDataSourceSpy).not.toHaveBeenCalled();
    expect(notificationMetadataSpy).not.toHaveBeenCalled();
    expect(metadataGet).not.toHaveBeenCalled();
    expect(deckManifestSpy).not.toHaveBeenCalled();
    expect(routerPluginSpy).not.toHaveBeenCalled();
    expect(State.ExecutionState.filterModel).toBe(originalFilterModel);
    expect(cacheInitializeSpy).not.toHaveBeenCalled();
    expect(renderSpy).not.toHaveBeenCalled();
    expect(listenSpy).not.toHaveBeenCalled();
    expect(syncSpy).not.toHaveBeenCalled();
  });

  it('does not request dynamic metadata before authentication succeeds', async () => {
    const root = createRoot();
    const authentication = deferred<boolean>();
    authenticationSpy.and.returnValue(authentication.promise);

    const bootstrap = bootstrapDeck(root);

    expect(notificationMetadataSpy).not.toHaveBeenCalled();
    expect(metadataGet).not.toHaveBeenCalled();
    authentication.resolve(true);
    await bootstrap;
    expect(notificationMetadataSpy).toHaveBeenCalledTimes(1);
    expect(metadataGet.calls.allArgs().map(([config]) => config.url)).toEqual(
      jasmine.arrayWithExactContents([
        jasmine.stringMatching(/jobs\/preconfigured$/),
        jasmine.stringMatching(/webhooks\/preconfigured$/),
      ]),
    );
  });

  it('does not wait for dynamic metadata before plugins and router startup', async () => {
    const root = createRoot();
    notificationMetadataSpy.and.returnValue(new Promise(() => undefined));

    await bootstrapDeck(root);

    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(routerPluginSpy).toHaveBeenCalled();
    expect(listenSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('waits for plugin initialization before constructing the router', async () => {
    const root = createRoot();
    const plugins = deferred<any[]>();
    deckManifestSpy.and.returnValue(plugins.promise);

    const bootstrap = bootstrapDeck(root);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(routerPluginSpy).not.toHaveBeenCalled();
    plugins.resolve([]);
    await bootstrap;
    expect(routerPluginSpy).toHaveBeenCalled();
  });

  it('waits for plugin initialization before binding direct runtime services', async () => {
    const root = createRoot();
    const plugins = deferred<any[]>();
    const pluginsStarted = deferred<void>();
    deckManifestSpy.and.callFake(() => {
      pluginsStarted.resolve(undefined);
      return plugins.promise;
    });
    const bindRuntime = spyOn(AngularServices, 'bindRuntime').and.callThrough();

    const bootstrap = bootstrapDeck(root);
    await pluginsStarted.promise;

    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(bindRuntime).not.toHaveBeenCalled();
    expect(routerPluginSpy).not.toHaveBeenCalled();
    plugins.resolve([]);
    await bootstrap;
    expect(bindRuntime).toHaveBeenCalledTimes(1);
    expect(bindRuntime).toHaveBeenCalledBefore(routerPluginSpy);
  });

  it('continues startup after initializePlugins settles plugin attempt failures', async () => {
    const root = createRoot();
    pluginLoadsSpy.and.returnValue(Promise.resolve([undefined]));

    await bootstrapDeck(root);

    expect(routerPluginSpy).toHaveBeenCalled();
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect(listenSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('starts cache initialization before render without waiting for it', async () => {
    const order: string[] = [];
    const root = createRoot();
    cacheInitializeSpy.and.callFake(() => {
      order.push('cache started');
      return new Promise<any[]>(() => undefined);
    });
    renderSpy.and.callFake(() => {
      order.push('render');
      return null;
    });
    listenSpy.and.callFake(() => order.push('listen'));

    await bootstrapDeck(root);

    expect(order).toEqual(['cache started', 'render', 'listen']);
  });

  it('logs a cache rejection exactly once without rejecting bootstrap', async () => {
    const root = createRoot();
    const failure = new Error('cache unavailable');
    const consoleError = spyOn(console, 'error');
    cacheInitializeSpy.and.returnValue(Promise.reject(failure));

    await expectAsync(bootstrapDeck(root)).toBeResolved();

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to initialize infrastructure caches', failure);
    expect(listenSpy).toHaveBeenCalledTimes(1);
  });

  it('isolates a synchronous cache initialization failure', async () => {
    const root = createRoot();
    const failure = new Error('cache initialization threw');
    const consoleError = spyOn(console, 'error');
    cacheInitializeSpy.and.callFake(() => {
      throw failure;
    });

    await expectAsync(bootstrapDeck(root)).toBeResolved();
    await Promise.resolve();

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to initialize infrastructure caches', failure);
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('assimilates a resolving cache thenable without blocking bootstrap', async () => {
    const root = createRoot();
    const consoleError = spyOn(console, 'error');
    cacheInitializeSpy.and.returnValue({
      then: (resolve: (value: any[]) => void) => resolve([]),
    } as any);

    await expectAsync(bootstrapDeck(root)).toBeResolved();
    await Promise.resolve();

    expect(consoleError).not.toHaveBeenCalled();
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('assimilates and logs a rejecting cache thenable exactly once', async () => {
    const root = createRoot();
    const failure = new Error('cache thenable rejected');
    const consoleError = spyOn(console, 'error');
    cacheInitializeSpy.and.returnValue({
      then: (_resolve: (value: any[]) => void, reject: (error: Error) => void) => reject(failure),
    } as any);

    await expectAsync(bootstrapDeck(root)).toBeResolved();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to initialize infrastructure caches', failure);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('shares one attempt and runtime across concurrent and sequential same-root calls', async () => {
    const root = createRoot();

    const first = bootstrapDeck(root);
    const concurrent = bootstrapDeck(root);

    expect(concurrent).toBe(first);
    await first;
    const sequential = bootstrapDeck(root);
    expect(sequential).toBe(first);
    await sequential;

    expect(authenticationSpy).toHaveBeenCalledTimes(1);
    expect(notificationMetadataSpy).toHaveBeenCalledTimes(1);
    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(pluginLoadsSpy).toHaveBeenCalledTimes(1);
    expect(routerPluginSpy).toHaveBeenCalledTimes(3);
    expect(cacheInitializeSpy).toHaveBeenCalledTimes(1);
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect(listenSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('rejects a competing root while bootstrap is active', async () => {
    const root = createRoot();
    const competingRoot = createRoot();
    const authentication = deferred<boolean>();
    authenticationSpy.and.returnValue(authentication.promise);

    const activeBootstrap = bootstrapDeck(root);

    await expectAsync(bootstrapDeck(competingRoot)).toBeRejectedWithError(
      'Cannot bootstrap Deck: a different root already owns the runtime',
    );
    authentication.resolve(true);
    await activeBootstrap;
    expect(renderSpy).toHaveBeenCalledTimes(1);
  });

  it('cleans up a state initialization failure and allows retry on the same root', async () => {
    const root = createRoot();
    const failure = new Error('state initialization failed');
    Object.defineProperty(State.ExecutionState, 'filterModel', {
      configurable: true,
      set: () => {
        throw failure;
      },
    });
    const dispose = spyOn(UIRouterReact.prototype, 'dispose').and.callThrough();

    await expectAsync(bootstrapDeck(root)).toBeRejectedWith(failure);

    expect(getDirectRouter()).toBeNull();
    expect(dispose.calls.all().filter(({ args }) => args.length === 0).length).toBe(1);
    expect(renderSpy).not.toHaveBeenCalled();
    expect(unmountSpy).not.toHaveBeenCalled();
    expect(syncSpy).not.toHaveBeenCalled();

    Object.defineProperty(State.ExecutionState, 'filterModel', {
      configurable: true,
      writable: true,
      value: null,
    });
    await expectAsync(bootstrapDeck(root)).toBeResolved();
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('unmounts a partial render, preserves a replacement global, and retries on the same root', async () => {
    const root = createRoot();
    const replacementRouter = new UIRouterReact();
    configuredRouters.push(replacementRouter);
    const failure = new Error('render failed');
    const dispose = spyOn(UIRouterReact.prototype, 'dispose').and.callThrough();
    cacheInitializeSpy.and.returnValue(new Promise<any[]>(() => undefined));
    renderSpy.and.callFake(() => {
      setDirectRouter(replacementRouter);
      throw failure;
    });

    await expectAsync(bootstrapDeck(root)).toBeRejectedWith(failure);

    expect(getDirectRouter()).toBe(replacementRouter);
    expect(dispose.calls.all().filter(({ args }) => args.length === 0).length).toBe(1);
    expect(unmountSpy.calls.allArgs().filter(([unmountedRoot]) => unmountedRoot === root).length).toBe(1);
    expect(syncSpy).not.toHaveBeenCalled();

    renderSpy.and.callThrough();
    await expectAsync(bootstrapDeck(root)).toBeResolved();
    expect(renderSpy).toHaveBeenCalledTimes(2);
    expect(syncSpy).toHaveBeenCalledTimes(1);
    expect(cacheInitializeSpy).toHaveBeenCalledTimes(2);
    expect(versionSchedulerCount()).toBe(1);
  });

  it('cleans up a router start failure and allows a different root to retry', async () => {
    const root = createRoot();
    const retryRoot = createRoot();
    const failure = new Error('router start failed');
    const cacheFailure = new Error('cache failed before router start');
    const consoleError = spyOn(console, 'error');
    const dispose = spyOn(UIRouterReact.prototype, 'dispose').and.callThrough();
    const start = spyOn(UIRouterReact.prototype, 'start').and.throwError(failure);
    cacheInitializeSpy.and.returnValue(Promise.reject(cacheFailure));

    await expectAsync(bootstrapDeck(root)).toBeRejectedWith(failure);

    expect(getDirectRouter()).toBeNull();
    expect(dispose.calls.all().filter(({ args }) => args.length === 0).length).toBe(1);
    expect(unmountSpy.calls.allArgs().filter(([unmountedRoot]) => unmountedRoot === root).length).toBe(1);
    expect(syncSpy).not.toHaveBeenCalled();

    start.and.callThrough();
    await expectAsync(bootstrapDeck(retryRoot)).toBeResolved();
    expect(renderSpy).toHaveBeenCalledTimes(2);
    expect(syncSpy).toHaveBeenCalledTimes(1);
    expect(cacheInitializeSpy).toHaveBeenCalledTimes(2);
    expect(versionSchedulerCount()).toBe(1);
    expect(consoleError.calls.allArgs()).toEqual([
      ['Failed to initialize infrastructure caches', cacheFailure],
      ['Failed to initialize infrastructure caches', cacheFailure],
    ]);
  });

  it('preserves the bootstrap error and permits retry when failure cleanup cannot unmount', async () => {
    const root = createRoot();
    const bootstrapFailure = new Error('render failed');
    const unmountFailure = new Error('unmount failed');
    const consoleError = spyOn(console, 'error');
    const dispose = spyOn(UIRouterReact.prototype, 'dispose').and.callThrough();
    renderSpy.and.throwError(bootstrapFailure);
    unmountSpy.and.throwError(unmountFailure);

    await expectAsync(bootstrapDeck(root)).toBeRejectedWith(bootstrapFailure);

    expect(getDirectRouter()).toBeNull();
    expect(dispose.calls.all().filter(({ args }) => args.length === 0).length).toBe(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to unmount Deck runtime', unmountFailure);

    renderSpy.and.callThrough();
    unmountSpy.and.callThrough();
    await expectAsync(bootstrapDeck(root)).toBeResolved();
    expect(renderSpy).toHaveBeenCalledTimes(2);
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });

  it('clears retained state and cache initialization during explicit reset', async () => {
    const firstRoot = createRoot();
    const secondRoot = createRoot();

    await bootstrapDeck(firstRoot);
    resetBootstrapDeckForTests();
    await bootstrapDeck(secondRoot);

    expect(cacheInitializeSpy).toHaveBeenCalledTimes(2);
    expect(versionSchedulerCount()).toBe(2);
  });

  it('disposes the bootstrap-owned runtime during explicit reset', async () => {
    const root = createRoot();
    let runtime: DeckRuntime;
    renderSpy.and.callFake((element: React.ReactElement) => {
      runtime = element.props.value;
      return null;
    });

    await bootstrapDeck(root);
    const dispose = spyOn(runtime, 'dispose').and.callThrough();

    resetBootstrapDeckForTests();

    expect(dispose).toHaveBeenCalledTimes(1);
  });

  it('clears scheduled and router-bound work on rebootstrap', async () => {
    jasmine.clock().install();
    try {
      const firstRoot = createRoot();
      const secondRoot = createRoot();
      let firstRuntime: DeckRuntime;
      renderSpy.and.callFake((element: React.ReactElement) => {
        firstRuntime = element.props.value;
        return null;
      });

      await bootstrapDeck(firstRoot);
      const firstRouter = getDirectRouter();
      const scheduled = jasmine.createSpy('scheduled');
      AngularServices.$timeout(scheduled, 100);

      expect(AngularServices.$timeout as any).toBe(firstRuntime.timeoutService as any);

      resetBootstrapDeckForTests();
      jasmine.clock().tick(100);
      await bootstrapDeck(secondRoot);

      expect(scheduled).not.toHaveBeenCalled();
      expect(getDirectRouter()).not.toBe(firstRouter);
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
