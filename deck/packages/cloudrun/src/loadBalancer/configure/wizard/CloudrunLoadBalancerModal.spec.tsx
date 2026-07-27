import { CloudrunLoadBalancerModalComponent as CloudrunLoadBalancerModal } from './CloudrunLoadBalancerModal';

describe('CloudrunLoadBalancerModal', () => {
  function buildModal(overrides: any = {}) {
    const props = {
      app: {
        loadBalancers: { refresh: jasmine.createSpy('refresh'), onNextRefresh: jasmine.createSpy('onNextRefresh') },
      },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      isNew: false,
      loadBalancer: { name: 'service', account: 'test', region: 'us-central1' },
      router: {},
      stateParams: {},
      stateService: { go: jasmine.createSpy('go'), includes: () => false },
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
    const firstUnsubscribe = jasmine.createSpy('firstUnsubscribe');
    const secondUnsubscribe = jasmine.createSpy('secondUnsubscribe');
    const callbacks: Array<() => void> = [];
    const onNextRefresh = jasmine.createSpy('onNextRefresh').and.callFake((callback: () => void) => {
      callbacks.push(callback);
      return callbacks.length === 1 ? firstUnsubscribe : secondUnsubscribe;
    });
    const refresh = jasmine.createSpy('refresh');
    const modal = buildModal({ app: { loadBalancers: { onNextRefresh, refresh } } }) as any;

    modal.onTaskComplete();

    expect(onNextRefresh.calls.first().invocationOrder).toBeLessThan(refresh.calls.first().invocationOrder);

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
