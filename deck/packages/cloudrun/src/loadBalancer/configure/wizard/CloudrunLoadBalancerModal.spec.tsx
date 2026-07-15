import { TaskMonitor } from '@spinnaker/core';

import { CloudrunLoadBalancerModal } from './CloudrunLoadBalancerModal';

describe('CloudrunLoadBalancerModal', () => {
  beforeEach(() => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
  });

  function buildModal(overrides: any = {}) {
    const props = {
      app: {
        loadBalancers: { refresh: jasmine.createSpy('refresh'), onNextRefresh: jasmine.createSpy('onNextRefresh') },
      },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      isNew: false,
      loadBalancer: { name: 'service', account: 'test', region: 'us-central1' },
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
});
