import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { registerLoadBalancerDataSource } from './loadBalancer.dataSource';

describe('direct runtime load balancer data source registration', () => {
  let runtime: DeckRuntime;

  beforeEach(() => {
    runtime = createDeckRuntime();
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => {
    runtime.dispose();
    ApplicationDataSourceRegistry.clearDataSources();
  });

  it('loads through the shared reader and returns a Promise-compatible onLoad result', async () => {
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
