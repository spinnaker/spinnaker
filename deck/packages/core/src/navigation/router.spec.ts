import { HashLocationService, UIRouterReact } from '@uirouter/react';
import { UrlService } from '@uirouter/core';
import * as ngimport from 'ngimport';

import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { AngularServices } from '../angular/services';
import { getDirectRouter, setDirectRouter } from './directRouter';
import { configureRouter, startRouter } from './router';
import {
  getRootStateRegistrationsForTests,
  registerRootState,
  resetRootStateRegistrationsForTests,
} from './rootState.registration';
import { StateConfigProvider } from './state.provider';

describe('configureRouter', () => {
  const routers: UIRouterReact[] = [];
  let originalRegistrations: ReturnType<typeof getRootStateRegistrationsForTests>;

  function createRouter(): UIRouterReact {
    const router = configureRouter();
    routers.push(router);
    return router;
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

  it('applies root state registrations to the direct UI Router state registry', () => {
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'registeredRoot', url: '/registered-root' }));

    const router = createRouter();

    expect(router.stateRegistry.get('home.registeredRoot')).toBeDefined();
  });

  it('registers the default fallback and trailing slash rewrite rules', () => {
    const router = createRouter();

    expect(router.urlRouter.rules().length).toBeGreaterThanOrEqual(2);
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
    const originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;
    window.location.hash = '';
    registerRootState((stateConfig) => stateConfig.addToRootState({ name: 'registeredRoot', url: '/registered-root' }));

    const router = createRouter();
    const stateChanges: string[] = [];
    const subscription = AngularServices.stateEvents.stateChangeSuccess.subscribe(({ to }) =>
      stateChanges.push(to.name),
    );

    try {
      await router.stateService.go('home.registeredRoot');

      expect(stateChanges).toContain('home.registeredRoot');
    } finally {
      subscription.unsubscribe();
      (ngimport as any).$injector = originalInjector;
    }
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

    expect(() => configureRouter()).toThrow(failure);

    expect(getDirectRouter()).toBe(previousRouter);
    const ownershipDisposals = dispose.calls.all().filter(({ args }) => args.length === 0);
    expect(ownershipDisposals.length).toBe(1);
    expect(ownershipDisposals[0].object).not.toBe(previousRouter);
    expect(listen.calls.allArgs().filter((args) => args.length === 0).length).toBe(0);
    expect(sync).not.toHaveBeenCalled();
  });
});
