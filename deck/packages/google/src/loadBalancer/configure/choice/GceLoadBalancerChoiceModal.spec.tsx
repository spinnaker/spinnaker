import React from 'react';
import { shallow } from 'enzyme';

import { ReactModal } from '@spinnaker/core';

import { GceProxyLoadBalancerModal } from '../common/GceProxyLoadBalancerModal';
import { GceHttpLoadBalancerModal } from '../http/GceHttpLoadBalancerModal';
import { GceNetworkLoadBalancerModal } from '../network/GceNetworkLoadBalancerModal';
import {
  GCE_LOAD_BALANCER_CHOICES,
  GceLoadBalancerChoiceModal,
  getGceLoadBalancerModal,
} from './GceLoadBalancerChoiceModal';

describe('GceLoadBalancerChoiceModal', () => {
  const application = { name: 'fnord' } as any;

  it('routes every exposed GCE load balancer type to its React modal', () => {
    expect(GCE_LOAD_BALANCER_CHOICES.map(({ type }) => type)).toEqual([
      'NETWORK',
      'INTERNAL',
      'TCP',
      'SSL',
      'HTTP',
      'INTERNAL_MANAGED',
    ]);
    expect(getGceLoadBalancerModal('NETWORK')).toBe(GceNetworkLoadBalancerModal);
    (['INTERNAL', 'TCP', 'SSL'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type), type).toBe(GceProxyLoadBalancerModal);
    });
    (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type), type).toBe(GceHttpLoadBalancerModal);
    });
  });

  it('advertises pipeline support only at the fully routed choice entry point', () => {
    expect(GceLoadBalancerChoiceModal.supportsPipelineConfig).toBe(true);
  });

  it('passes modal sizing as ReactModal dialog options instead of component props', () => {
    const result = Promise.resolve();
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(result);

    const opened = GceLoadBalancerChoiceModal.show({ application } as any);

    expect(opened).toBe(result);
    expect(show).toHaveBeenCalledExactlyOnceWith(
      GceLoadBalancerChoiceModal,
      { application },
      { dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg' },
    );
  });

  it('renders native modal sections and controls', () => {
    const wrapper = shallow(<GceLoadBalancerChoiceModal application={application} />);

    expect(wrapper.find('.modal-header')).toHaveSize(1);
    expect(wrapper.find('.modal-body')).toHaveSize(1);
    expect(wrapper.find('.modal-footer')).toHaveSize(1);
    expect(wrapper.find('button.btn.btn-primary').text()).toContain('Configure Load Balancer');
  });

  it('renders each type card as a native pressed-state button', () => {
    const wrapper = shallow(<GceLoadBalancerChoiceModal application={application} />);
    const choices = wrapper.find('button.card');

    expect(choices).toHaveSize(GCE_LOAD_BALANCER_CHOICES.length);
    choices.forEach((choice, index) => {
      expect(choice.prop('type'), GCE_LOAD_BALANCER_CHOICES[index].type).toBe('button');
      expect(choice.prop('aria-pressed'), GCE_LOAD_BALANCER_CHOICES[index].type).toBe(index === 0);
      expect(choice.prop('disabled'), GCE_LOAD_BALANCER_CHOICES[index].type).toBe(false);
    });

    choices.at(2).simulate('click');
    wrapper.update();

    expect(wrapper.find('button.card').at(0).prop('aria-pressed')).toBe(false);
    expect(wrapper.find('button.card').at(2).prop('aria-pressed')).toBe(true);
    expect(wrapper.find('button.card').at(2).hasClass('active')).toBe(true);
  });

  it('opens every exposed type in create and pipeline modes', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);

    ([false, true] as const).forEach((forPipelineConfig) => {
      GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
        const modal = new GceLoadBalancerChoiceModal({ application, forPipelineConfig } as any);
        modal.state = { ...modal.state, selectedChoice: choice };

        (modal as any).choose();

        expect(
          getGceLoadBalancerModal(choice.type).show,
          `${choice.type} ${forPipelineConfig ? 'pipeline' : 'create'}`,
        ).toHaveBeenCalledWith(
          expect.objectContaining({
            forPipelineConfig,
            isNew: !forPipelineConfig,
            loadBalancer: null,
            loadBalancerType: choice.type,
            mode: forPipelineConfig ? 'pipeline' : 'create',
          }),
        );
      });
    });
  });

  it('opens a selected type in create mode without persisted data', () => {
    const closeModal = vi.fn();
    const result = Promise.resolve({ loadBalancerType: 'SSL' });
    const show = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(result as any);
    const modal = new GceLoadBalancerChoiceModal({ application, closeModal } as any);

    modal.state = { ...modal.state, selectedChoice: GCE_LOAD_BALANCER_CHOICES[3] };
    (modal as any).choose();

    expect(show).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        app: application,
        application,
        forPipelineConfig: false,
        isNew: true,
        loadBalancer: null,
        loadBalancerType: 'SSL',
        mode: 'create',
      }),
    );
    expect(closeModal).toHaveBeenCalledExactlyOnceWith(result);
  });

  it('opens a selected type in pipeline mode and propagates its command promise', async () => {
    const closeModal = vi.fn();
    const command = { loadBalancerType: 'INTERNAL_MANAGED', type: 'upsertLoadBalancer' };
    const result = Promise.resolve(command);
    const show = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(result);
    const modal = new GceLoadBalancerChoiceModal({ application, closeModal, forPipelineConfig: true } as any);

    modal.state = { ...modal.state, selectedChoice: GCE_LOAD_BALANCER_CHOICES[5] };
    (modal as any).choose();

    expect(show).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        forPipelineConfig: true,
        loadBalancer: null,
        loadBalancerType: 'INTERNAL_MANAGED',
        mode: 'pipeline',
      }),
    );
    expect(closeModal).toHaveBeenCalledExactlyOnceWith(result);
    await expectAsync(closeModal.mock.lastCall[0]).toBeResolvedTo(command);
  });

  it('routes every exposed type directly to edit mode with the current load balancer', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer: current } as any);

      expect(getGceLoadBalancerModal(choice.type).show, choice.type).toHaveBeenCalledWith(
        expect.objectContaining({
          app: application,
          application,
          forPipelineConfig: false,
          isNew: false,
          loadBalancer: current,
          loadBalancerType: choice.type,
          mode: 'edit',
        }),
      );
    });

    expect(reactModalShow).not.toHaveBeenCalled();
  });

  it('routes every existing type to pipeline mode when editing pipeline configuration', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({
        application,
        forPipelineConfig: true,
        isNew: false,
        loadBalancer: current,
      } as any);

      expect(getGceLoadBalancerModal(choice.type).show, choice.type).toHaveBeenCalledWith(
        expect.objectContaining({
          app: application,
          application,
          forPipelineConfig: true,
          isNew: false,
          loadBalancer: current,
          loadBalancerType: choice.type,
          mode: 'pipeline',
        }),
      );
    });

    expect(reactModalShow).not.toHaveBeenCalled();
  });

  ([undefined, '', 'http', 'UNKNOWN'] as const).forEach((persistedType) => {
    it(`blocks edit routing for unsupported persisted type ${String(persistedType)}`, () => {
      const networkShow = vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(undefined);
      const proxyShow = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(undefined);
      const httpShow = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(undefined);
      const blockedResult = Promise.resolve();
      const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(blockedResult);
      const loadBalancer = { name: 'fnord', loadBalancerType: persistedType };

      const result = GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer } as any);

      expect(result).toBe(blockedResult);
      expect(reactModalShow).toHaveBeenCalledExactlyOnceWith(
        GceLoadBalancerChoiceModal,
        { application, isNew: false, loadBalancer },
        { dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg' },
      );
      expect(networkShow).not.toHaveBeenCalled();
      expect(proxyShow).not.toHaveBeenCalled();
      expect(httpShow).not.toHaveBeenCalled();
    });
  });

  ([undefined, 'http', 'UNKNOWN'] as const).forEach((persistedType) => {
    it(`renders a non-submittable blocked pipeline-edit state for ${String(persistedType)}`, () => {
      const wrapper = shallow(
        <GceLoadBalancerChoiceModal
          application={application}
          forPipelineConfig={true}
          isNew={false}
          loadBalancer={{ name: 'fnord', loadBalancerType: persistedType }}
        />,
      );

      expect(wrapper.find('[role="alert"]').text()).toContain('cannot be edited');
      expect(wrapper.find('[role="alert"]').text()).toContain(persistedType || 'missing');
      expect(wrapper.find('button.card')).toHaveSize(GCE_LOAD_BALANCER_CHOICES.length);
      wrapper.find('button.card').forEach((choice) => {
        expect(choice.prop('disabled')).toBe(true);
        expect(choice.prop('aria-pressed')).toBe(false);
      });
      expect(wrapper.find('button.btn.btn-primary').prop('disabled')).toBe(true);
    });
  });

  it('does not open a type modal when blocked submission is invoked programmatically', () => {
    const networkShow = vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(undefined);
    const proxyShow = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(undefined);
    const httpShow = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(undefined);
    const modal = new GceLoadBalancerChoiceModal({
      application,
      forPipelineConfig: true,
      isNew: false,
      loadBalancer: { name: 'fnord', loadBalancerType: 'http' },
    } as any);

    (modal as any).choose();

    expect(networkShow).not.toHaveBeenCalled();
    expect(proxyShow).not.toHaveBeenCalled();
    expect(httpShow).not.toHaveBeenCalled();
  });
});
