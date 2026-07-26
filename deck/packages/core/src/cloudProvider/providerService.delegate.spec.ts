import type { IQService } from 'angular';

import { SETTINGS } from '../config';
import { CloudProviderRegistry } from './CloudProviderRegistry';
import { DirectProviderServiceDelegate } from './providerService.delegate';

describe('DirectProviderServiceDelegate', () => {
  const provider = 'directProviderServiceDelegateTest';
  const otherProvider = 'otherDirectProviderServiceDelegateTest';
  const serviceKey = 'test.service';
  const otherServiceKey = 'test.otherService';

  beforeEach(() => {
    SETTINGS.providers[provider] = {};
    SETTINGS.providers[otherProvider] = {};
  });

  afterEach(() => {
    delete SETTINGS.providers[provider];
    delete SETTINGS.providers[otherProvider];
    (CloudProviderRegistry as any).providers.delete(provider);
    (CloudProviderRegistry as any).providers.delete(otherProvider);
  });

  it('checks for a delegate without constructing it', () => {
    const constructor = jasmine.createSpy('constructor');
    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: constructor },
    });

    const delegate = new DirectProviderServiceDelegate({} as IQService);

    expect(delegate.hasDelegate(provider, serviceKey)).toBe(true);
    expect(constructor).not.toHaveBeenCalled();
  });

  it('constructs the registered delegate with its promise dependency', () => {
    const promiseService = {} as IQService;
    class TestService {
      constructor(public readonly injectedPromiseService: IQService) {}
    }
    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: TestService },
    });

    const service = new DirectProviderServiceDelegate(promiseService).getDelegate<TestService>(provider, serviceKey);

    expect(service).toEqual(jasmine.any(TestService));
    expect(service.injectedPromiseService).toBe(promiseService);
  });

  it('only injects runtime services into delegates that opt in', () => {
    const runtimeServices = {} as any;
    class ExistingService {
      constructor(_promiseService: IQService, public readonly existingSecondDependency = 'existing') {}
    }
    class RuntimeAwareService {
      public static readonly requiresDeckRuntimeServices = true;
      constructor(_promiseService: IQService, public readonly injectedRuntimeServices?: any) {}
    }
    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: ExistingService, otherService: RuntimeAwareService },
    });
    const delegate = new DirectProviderServiceDelegate({} as IQService);
    delegate.bindRuntimeServices(runtimeServices);

    expect(delegate.getDelegate<ExistingService>(provider, serviceKey).existingSecondDependency).toBe('existing');
    expect(delegate.getDelegate<RuntimeAwareService>(provider, otherServiceKey).injectedRuntimeServices).toBe(
      runtimeServices,
    );
  });

  it('caches each service instance by provider and service key', () => {
    class TestService {}
    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: TestService, otherService: TestService },
    });
    CloudProviderRegistry.registerProvider(otherProvider, {
      name: otherProvider,
      test: { service: TestService },
    });
    const delegate = new DirectProviderServiceDelegate({} as IQService);
    const service = delegate.getDelegate(provider, serviceKey);

    expect(delegate.getDelegate(provider, serviceKey)).toBe(service);
    expect(delegate.getDelegate(provider, otherServiceKey)).not.toBe(service);
    expect(delegate.getDelegate(otherProvider, serviceKey)).not.toBe(service);
  });

  it('resolves registrations made after the delegate is created', () => {
    class TestService {}
    const delegate = new DirectProviderServiceDelegate({} as IQService);

    expect(delegate.hasDelegate(provider, serviceKey)).toBe(false);

    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: TestService },
    });

    expect(delegate.getDelegate(provider, serviceKey)).toEqual(jasmine.any(TestService));
  });

  it('clears cached instances when disposed', () => {
    class TestService {}
    CloudProviderRegistry.registerProvider(provider, {
      name: provider,
      test: { service: TestService },
    });
    const delegate = new DirectProviderServiceDelegate({} as IQService);
    const firstService = delegate.getDelegate(provider, serviceKey);

    delegate.dispose();

    expect(delegate.getDelegate(provider, serviceKey)).not.toBe(firstService);
  });

  it('throws the established error when no delegate is registered', () => {
    const delegate = new DirectProviderServiceDelegate({} as IQService);

    expect(() => delegate.getDelegate(provider, serviceKey)).toThrowError(
      'No "test.service" service found for provider "directProviderServiceDelegateTest"',
    );
  });
});
