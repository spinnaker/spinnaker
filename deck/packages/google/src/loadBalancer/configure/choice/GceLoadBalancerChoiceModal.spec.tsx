import React from 'react';
import { shallow } from 'enzyme';

import { ReactModal } from '@spinnaker/core';

import { GceProxyLoadBalancerModal } from '../common/GceProxyLoadBalancerModal';
import { GceHttpLoadBalancerModal } from '../http/GceHttpLoadBalancerModal';
import { GceNetworkLoadBalancerModal } from '../network/GceNetworkLoadBalancerModal';
import { GceRegionalExternalNetworkLoadBalancerModal } from '../network/GceRegionalExternalNetworkLoadBalancerModal';
import {
  GCE_LOAD_BALANCER_CHOICES,
  GceLoadBalancerChoiceModal,
  getGceLoadBalancerModal,
} from './GceLoadBalancerChoiceModal';

describe('GceLoadBalancerChoiceModal', () => {
  const application = { name: 'fnord' } as any;

  const spyOnAllGceLoadBalancerModals = (impl: (props: any) => Promise<any> = () => Promise.resolve() as any): void => {
    spyOn(GceNetworkLoadBalancerModal, 'show').and.callFake(impl);
    spyOn(GceProxyLoadBalancerModal, 'show').and.callFake(impl);
    spyOn(GceHttpLoadBalancerModal, 'show').and.callFake(impl);
    spyOn(GceRegionalExternalNetworkLoadBalancerModal, 'show').and.callFake(impl);
  };

  it('routes every exposed GCE load balancer type to its React modal', () => {
    expect(GCE_LOAD_BALANCER_CHOICES.map(({ type }) => type)).toEqual([
      'NETWORK',
      'INTERNAL',
      'TCP',
      'SSL',
      'HTTP',
      'INTERNAL_MANAGED',
      'EXTERNAL_MANAGED',
      'REGIONAL_EXTERNAL_NETWORK',
    ]);
    expect(getGceLoadBalancerModal('NETWORK')).toBe(GceNetworkLoadBalancerModal);
    (['INTERNAL', 'TCP', 'SSL'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type)).withContext(type).toBe(GceProxyLoadBalancerModal);
    });
    (['HTTP', 'INTERNAL_MANAGED', 'EXTERNAL_MANAGED'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type)).withContext(type).toBe(GceHttpLoadBalancerModal);
    });
    expect(getGceLoadBalancerModal('REGIONAL_EXTERNAL_NETWORK')).toBe(GceRegionalExternalNetworkLoadBalancerModal);
  });

  it('advertises pipeline support only at the fully routed choice entry point', () => {
    expect(GceLoadBalancerChoiceModal.supportsPipelineConfig).toBe(true);
  });

  it('passes modal sizing as ReactModal dialog options instead of component props', () => {
    const result = Promise.resolve();
    const show = spyOn(ReactModal, 'show').and.returnValue(result);

    const opened = GceLoadBalancerChoiceModal.show({ application } as any);

    expect(opened).toBe(result);
    expect(show).toHaveBeenCalledOnceWith(
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
      expect(choice.prop('type')).withContext(GCE_LOAD_BALANCER_CHOICES[index].type).toBe('button');
      expect(choice.prop('aria-pressed'))
        .withContext(GCE_LOAD_BALANCER_CHOICES[index].type)
        .toBe(index === 0);
      expect(choice.prop('disabled')).withContext(GCE_LOAD_BALANCER_CHOICES[index].type).toBe(false);
    });

    choices.at(2).simulate('click');
    wrapper.update();

    expect(wrapper.find('button.card').at(0).prop('aria-pressed')).toBe(false);
    expect(wrapper.find('button.card').at(2).prop('aria-pressed')).toBe(true);
    expect(wrapper.find('button.card').at(2).hasClass('active')).toBe(true);
  });

  it('opens every exposed type in create and pipeline modes', () => {
    spyOnAllGceLoadBalancerModals();

    ([false, true] as const).forEach((forPipelineConfig) => {
      GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
        const modal = new GceLoadBalancerChoiceModal({ application, forPipelineConfig } as any);
        modal.state = { ...modal.state, selectedChoice: choice };

        (modal as any).choose();

        expect(getGceLoadBalancerModal(choice.type).show)
          .withContext(`${choice.type} ${forPipelineConfig ? 'pipeline' : 'create'}`)
          .toHaveBeenCalledWith(
            jasmine.objectContaining({
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
    const closeModal = jasmine.createSpy('closeModal');
    const result = Promise.resolve({ loadBalancerType: 'SSL' });
    const show = spyOn(GceProxyLoadBalancerModal, 'show').and.returnValue(result as any);
    const modal = new GceLoadBalancerChoiceModal({ application, closeModal } as any);

    modal.state = { ...modal.state, selectedChoice: GCE_LOAD_BALANCER_CHOICES[3] };
    (modal as any).choose();

    expect(show).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        app: application,
        application,
        forPipelineConfig: false,
        isNew: true,
        loadBalancer: null,
        loadBalancerType: 'SSL',
        mode: 'create',
      }),
    );
    expect(closeModal).toHaveBeenCalledOnceWith(result);
  });

  it('opens a selected type in pipeline mode and propagates its command promise', async () => {
    const closeModal = jasmine.createSpy('closeModal');
    const command = { loadBalancerType: 'INTERNAL_MANAGED', type: 'upsertLoadBalancer' };
    const result = Promise.resolve(command);
    const show = spyOn(GceHttpLoadBalancerModal, 'show').and.returnValue(result);
    const modal = new GceLoadBalancerChoiceModal({ application, closeModal, forPipelineConfig: true } as any);

    modal.state = { ...modal.state, selectedChoice: GCE_LOAD_BALANCER_CHOICES[5] };
    (modal as any).choose();

    expect(show).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        forPipelineConfig: true,
        loadBalancer: null,
        loadBalancerType: 'INTERNAL_MANAGED',
        mode: 'pipeline',
      }),
    );
    expect(closeModal).toHaveBeenCalledOnceWith(result);
    await expectAsync(closeModal.calls.mostRecent().args[0]).toBeResolvedTo(command);
  });

  it('routes every exposed type directly to edit mode with the current load balancer', () => {
    spyOnAllGceLoadBalancerModals((props: any) => Promise.resolve(props.loadBalancer) as any);
    const reactModalShow = spyOn(ReactModal, 'show');

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer: current } as any);

      expect(getGceLoadBalancerModal(choice.type).show)
        .withContext(choice.type)
        .toHaveBeenCalledWith(
          jasmine.objectContaining({
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
    spyOnAllGceLoadBalancerModals((props: any) => Promise.resolve(props.loadBalancer) as any);
    const reactModalShow = spyOn(ReactModal, 'show');

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({
        application,
        forPipelineConfig: true,
        isNew: false,
        loadBalancer: current,
      } as any);

      expect(getGceLoadBalancerModal(choice.type).show)
        .withContext(choice.type)
        .toHaveBeenCalledWith(
          jasmine.objectContaining({
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
      const networkShow = spyOn(GceNetworkLoadBalancerModal, 'show');
      const proxyShow = spyOn(GceProxyLoadBalancerModal, 'show');
      const httpShow = spyOn(GceHttpLoadBalancerModal, 'show');
      const regionalExternalNetworkShow = spyOn(GceRegionalExternalNetworkLoadBalancerModal, 'show');
      const blockedResult = Promise.resolve();
      const reactModalShow = spyOn(ReactModal, 'show').and.returnValue(blockedResult);
      const loadBalancer = { name: 'fnord', loadBalancerType: persistedType };

      const result = GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer } as any);

      expect(result).toBe(blockedResult);
      expect(reactModalShow).toHaveBeenCalledOnceWith(
        GceLoadBalancerChoiceModal,
        { application, isNew: false, loadBalancer },
        { dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg' },
      );
      expect(networkShow).not.toHaveBeenCalled();
      expect(proxyShow).not.toHaveBeenCalled();
      expect(httpShow).not.toHaveBeenCalled();
      expect(regionalExternalNetworkShow).not.toHaveBeenCalled();
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
    const networkShow = spyOn(GceNetworkLoadBalancerModal, 'show');
    const proxyShow = spyOn(GceProxyLoadBalancerModal, 'show');
    const httpShow = spyOn(GceHttpLoadBalancerModal, 'show');
    const regionalExternalNetworkShow = spyOn(GceRegionalExternalNetworkLoadBalancerModal, 'show');
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
    expect(regionalExternalNetworkShow).not.toHaveBeenCalled();
  });

  it('exposes EXTERNAL_MANAGED and REGIONAL_EXTERNAL_NETWORK choices', () => {
    expect(GCE_LOAD_BALANCER_CHOICES.map(({ type }) => type)).toEqual([
      'NETWORK',
      'INTERNAL',
      'TCP',
      'SSL',
      'HTTP',
      'INTERNAL_MANAGED',
      'EXTERNAL_MANAGED',
      'REGIONAL_EXTERNAL_NETWORK',
    ]);
  });

  it('routes EXTERNAL_MANAGED to the HTTP modal', () => {
    expect(getGceLoadBalancerModal('EXTERNAL_MANAGED' as any)).toBe(GceHttpLoadBalancerModal);
  });

  it('routes REGIONAL_EXTERNAL_NETWORK to its dedicated modal', () => {
    expect(getGceLoadBalancerModal('REGIONAL_EXTERNAL_NETWORK' as any)).toBe(
      GceRegionalExternalNetworkLoadBalancerModal,
    );
  });
});
