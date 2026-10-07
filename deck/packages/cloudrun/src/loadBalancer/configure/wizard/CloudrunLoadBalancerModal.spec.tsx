import { CloudrunLoadBalancerModalComponent as CloudrunLoadBalancerModal } from './CloudrunLoadBalancerModal';

describe('CloudrunLoadBalancerModal', () => {
  function buildModal(overrides: any = {}) {
    const props = {
      app: {
        loadBalancers: { refresh: vi.fn(), onNextRefresh: vi.fn() },
      },
      closeModal: vi.fn(),
      dismissModal: vi.fn(),
      isNew: false,
      loadBalancer: { name: 'service', account: 'test', region: 'us-central1' },
      router: {},
      stateParams: {},
      stateService: { go: vi.fn(), includes: () => false },
      ...overrides,
    } as any;

    return new CloudrunLoadBalancerModal(props);
  }

  it('dismisses the modal when edit conversion fails', async () => {
    const modal = buildModal();
    (modal as any).transformer = {
      convertLoadBalancerForEditing: () => Promise.reject(new Error('conversion failed')),
    };

    modal.componentDidMount();
    await Promise.resolve();
    await Promise.resolve();

    expect(modal.props.dismissModal).toHaveBeenCalled();
  });

  it('ignores application refresh callbacks after unmount', () => {
    const modal = buildModal();

    modal.componentWillUnmount();
    (modal as any).onApplicationRefresh();

    expect(modal.props.dismissModal).not.toHaveBeenCalled();
  });

  it('owns its refresh subscription across replacement and unmount', () => {
    const firstUnsubscribe = vi.fn();
    const secondUnsubscribe = vi.fn();
    const callbacks: Array<() => void> = [];
    const onNextRefresh = vi.fn().mockImplementation((callback: () => void) => {
      callbacks.push(callback);
      return callbacks.length === 1 ? firstUnsubscribe : secondUnsubscribe;
    });
    const refresh = vi.fn();
    const modal = buildModal({ app: { loadBalancers: { onNextRefresh, refresh } } }) as any;

    modal.onTaskComplete();

    expect(onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(refresh.mock.invocationCallOrder[0]);

    modal.onTaskComplete();

    expect(firstUnsubscribe).toHaveBeenCalledTimes(1);

    modal.componentWillUnmount();
    callbacks[1]();

    expect(secondUnsubscribe).toHaveBeenCalledTimes(1);
    expect(modal.applicationRefreshUnsubscribe).toBeUndefined();
    expect(modal.props.dismissModal).not.toHaveBeenCalled();
    expect(modal.props.stateService.go).not.toHaveBeenCalled();
  });

  it('opens updated load balancer details through the injected state service', () => {
    const modal = buildModal();
    modal.state.loadBalancer = { credentials: 'test', name: 'service', region: 'us-central1' } as any;

    (modal as any).onApplicationRefresh();

    expect(modal.props.stateService.go).toHaveBeenCalledWith('.loadBalancerDetails', {
      accountId: 'test',
      name: 'service',
      provider: 'cloudrun',
      region: 'us-central1',
    });
  });
});
