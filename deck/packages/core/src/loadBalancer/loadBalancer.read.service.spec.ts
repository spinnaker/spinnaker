import { mockHttpClient } from '../api/mock/jasmine';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { CloudProviderRegistry } from '../cloudProvider';
import { SETTINGS } from '../config';

describe('direct runtime load balancer reader', () => {
  const provider = 'directRuntimeLoadBalancerReaderTest';
  let runtime: DeckRuntime;

  beforeEach(() => {
    SETTINGS.providers[provider] = { enabled: true };
    runtime = createDeckRuntime();
  });

  afterEach(() => {
    runtime.dispose();
    delete SETTINGS.providers[provider];
    (CloudProviderRegistry as any).providers.delete(provider);
  });

  it('normalizes load balancers with the registered provider transformer', async () => {
    class TestLoadBalancerTransformer {
      public normalizeLoadBalancer(loadBalancer: any) {
        return Promise.resolve({ ...loadBalancer, transformed: true });
      }
    }

    CloudProviderRegistry.registerProvider(provider, {
      name: 'Direct Runtime Load Balancer Reader Test',
      loadBalancer: { transformer: TestLoadBalancerTransformer },
    });
    const http = mockHttpClient();
    http.expectGET('/applications/app/loadBalancers').respond(200, [
      {
        name: 'app-test-detail',
        provider,
      },
    ]);

    const loadBalancersPromise = runtime.services.loadBalancerReader.loadLoadBalancers('app');
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
});
