import {
  GceLoadBalancerDataController,
  IGceLoadBalancerDataReaders,
  mergeGceResourceOptions,
} from './gceLoadBalancerData';

describe('GCE load balancer data', () => {
  it('loads every editor data source and scopes regions to the selected account', async () => {
    const readers = testReaders();
    const controller = new GceLoadBalancerDataController(readers);

    await controller.load('test-account');

    expect(readers.accounts).toHaveBeenCalledWith();
    expect(readers.regions).toHaveBeenCalledWith('test-account');
    expect(readers.networks).toHaveBeenCalledWith();
    expect(readers.subnets).toHaveBeenCalledWith();
    expect(readers.certificates).toHaveBeenCalledWith();
    expect(readers.addresses).toHaveBeenCalledWith();
    expect(readers.healthChecks).toHaveBeenCalledWith();
    expect(readers.backendServices).toHaveBeenCalledWith();
    expect(controller.getState()).toEqual({
      data: {
        accounts: [{ name: 'test-account' }],
        addresses: [{ name: 'address' }],
        backendServices: [{ name: 'backend' }],
        certificates: [{ name: 'certificate' }],
        healthChecks: [{ name: 'check' }],
        networks: [{ name: 'network' }],
        regions: [{ name: 'europe-west1' }],
        subnets: [{ name: 'subnet' }],
      },
      error: undefined,
      status: 'ready',
    });
  });

  it('publishes only the latest request', async () => {
    const first = deferred<unknown[]>();
    const second = deferred<unknown[]>();
    const readers = testReaders();
    readers.regions.and.callFake((account: string) => (account === 'first' ? first.promise : second.promise));
    const controller = new GceLoadBalancerDataController(readers);

    const firstLoad = controller.load('first');
    const secondLoad = controller.load('second');
    second.resolve([{ name: 'second-region' }]);
    await secondLoad;
    first.resolve([{ name: 'stale-region' }]);
    await firstLoad;

    expect(controller.getState().data.regions).toEqual([{ name: 'second-region' }]);
    expect(controller.getState().status).toBe('ready');
  });

  it('reports an error from the latest request', async () => {
    const readers = testReaders();
    const failure = new Error('regions failed');
    readers.regions.and.returnValue(Promise.reject(failure));
    const controller = new GceLoadBalancerDataController(readers);

    await controller.load('test-account');

    expect(controller.getState().status).toBe('error');
    expect(controller.getState().error).toBe(failure);
  });

  it('does not publish responses after disposal', async () => {
    const pendingRegions = deferred<unknown[]>();
    const readers = testReaders();
    readers.regions.and.returnValue(pendingRegions.promise);
    const controller = new GceLoadBalancerDataController(readers);
    const listener = jasmine.createSpy('listener');
    controller.subscribe(listener);

    const loading = controller.load('test-account');
    listener.calls.reset();
    controller.dispose();
    pendingRegions.resolve([{ name: 'late-region' }]);
    await loading;

    expect(listener).not.toHaveBeenCalled();
    expect(controller.getState().status).toBe('loading');
  });

  it('keeps persisted references that are missing from fresh reader data', () => {
    const available = [{ name: 'available' }];
    const persisted = [
      { name: 'available', selfLink: 'available-link' },
      { name: 'removed', selfLink: 'removed-link', staleMetadata: true },
    ];

    expect(mergeGceResourceOptions(available, persisted)).toEqual([
      { name: 'available' },
      { name: 'removed', selfLink: 'removed-link', staleMetadata: true },
    ]);
  });
});

function testReaders(): jasmine.SpyObj<IGceLoadBalancerDataReaders> {
  return jasmine.createSpyObj<IGceLoadBalancerDataReaders>('readers', {
    accounts: Promise.resolve([{ name: 'test-account' }]),
    addresses: Promise.resolve([{ name: 'address' }]),
    backendServices: Promise.resolve([{ name: 'backend' }]),
    certificates: Promise.resolve([{ name: 'certificate' }]),
    healthChecks: Promise.resolve([{ name: 'check' }]),
    networks: Promise.resolve([{ name: 'network' }]),
    regions: Promise.resolve([{ name: 'europe-west1' }]),
    subnets: Promise.resolve([{ name: 'subnet' }]),
  });
}

function deferred<T>() {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((resolver) => (resolve = resolver));
  return { promise, resolve: resolve! };
}
