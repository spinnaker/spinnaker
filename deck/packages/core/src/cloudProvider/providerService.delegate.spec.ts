import type { IQService } from 'angular';

import { SETTINGS } from '../config';
import { CloudProviderRegistry } from './CloudProviderRegistry';
import { DirectProviderServiceDelegate } from './providerService.delegate';

describe('DirectProviderServiceDelegate', () => {
  const provider = 'directProviderServiceDelegateTest';
  const serviceKey = 'test.service';

  beforeEach(() => {
    SETTINGS.providers[provider] = {};
  });

  afterEach(() => {
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
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

  it('throws the established error when no delegate is registered', () => {
    const delegate = new DirectProviderServiceDelegate({} as IQService);

    expect(() => delegate.getDelegate(provider, serviceKey)).toThrowError(
      'No "test.service" service found for provider "directProviderServiceDelegateTest"',
    );
  });
});
