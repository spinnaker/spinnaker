import { ReactModal } from '@spinnaker/core';

import { AzureLoadBalancerTypes } from '../../utility';
import { AzureLoadBalancerChoiceModal } from './AzureLoadBalancerChoiceModal';
import { AzureLoadBalancerModal } from './AzureLoadBalancerModal';

describe('AzureLoadBalancerChoiceModal', () => {
  it('declares support for pipeline config results', () => {
    expect((AzureLoadBalancerChoiceModal as any).supportsPipelineConfig).toBe(true);
  });

  it('opens the configure modal directly when editing an existing load balancer', () => {
    const application = { name: 'fnord' } as any;
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const loadBalancer = { name: 'fnord-main', loadBalancerType: 'Azure Load Balancer' };
    const configurePromise = Promise.resolve(loadBalancer);
    const show = spyOn(AzureLoadBalancerModal, 'show').and.returnValue(configurePromise as any);
    const reactModalShow = spyOn(ReactModal, 'show');

    const result = AzureLoadBalancerChoiceModal.show({
      application,
      closeModal,
      dismissModal,
      loadBalancer,
      isNew: false,
    } as any);

    expect(result).toBe(configurePromise as any);
    expect(reactModalShow).not.toHaveBeenCalled();
    expect(show).toHaveBeenCalledWith(
      jasmine.objectContaining({
        app: application,
        application,
        closeModal,
        dismissModal,
        loadBalancer,
        isNew: false,
        forPipelineConfig: false,
      }),
    );
  });

  it('opens the configure modal with the selected load balancer type', () => {
    const application = { name: 'fnord' } as any;
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const configurePromise = Promise.resolve();
    const show = spyOn(AzureLoadBalancerModal, 'show').and.returnValue(configurePromise as any);
    const modal = new AzureLoadBalancerChoiceModal({ application, closeModal, dismissModal } as any);
    const selectedChoice = AzureLoadBalancerTypes[1];

    modal.state = { ...modal.state, selectedChoice };
    (modal as any).choose();

    expect(dismissModal).not.toHaveBeenCalled();
    expect(closeModal).toHaveBeenCalledWith(configurePromise);
    expect(show).toHaveBeenCalledWith(
      jasmine.objectContaining({
        app: application,
        application,
        loadBalancer: null,
        isNew: true,
        forPipelineConfig: false,
        loadBalancerType: selectedChoice,
      }),
    );
  });

  it('propagates the configure modal result to the choice modal caller', async () => {
    const application = { name: 'fnord' } as any;
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const loadBalancer = { name: 'fnord-main' };
    const configurePromise = Promise.resolve(loadBalancer);
    const show = spyOn(AzureLoadBalancerModal, 'show').and.returnValue(configurePromise as any);
    const modal = new AzureLoadBalancerChoiceModal({
      application,
      closeModal,
      dismissModal,
      forPipelineConfig: true,
    } as any);
    const selectedChoice = AzureLoadBalancerTypes[1];

    modal.state = { ...modal.state, selectedChoice };
    (modal as any).choose();

    expect(dismissModal).not.toHaveBeenCalled();
    expect(show).toHaveBeenCalledWith({
      app: application,
      application,
      closeModal,
      dismissModal,
      forPipelineConfig: true,
      loadBalancer: null,
      isNew: true,
      loadBalancerType: selectedChoice,
    });
    expect(closeModal).toHaveBeenCalledWith(configurePromise);
    await expectAsync(closeModal.calls.mostRecent().args[0]).toBeResolvedTo(loadBalancer);
  });

  it('propagates configure modal dismissal through the choice modal promise', async () => {
    const application = { name: 'fnord' } as any;
    const closeModal = jasmine.createSpy('closeModal');
    const configurePromise = Promise.reject('cancelled');
    spyOn(AzureLoadBalancerModal, 'show').and.returnValue(configurePromise as any);
    const modal = new AzureLoadBalancerChoiceModal({ application, closeModal } as any);

    (modal as any).choose();

    expect(closeModal).toHaveBeenCalledWith(configurePromise);
    await expectAsync(closeModal.calls.mostRecent().args[0]).toBeRejectedWith('cancelled');
  });

  it('initializes the selected choice from an existing load balancer type', () => {
    const application = { name: 'fnord' } as any;
    const modal = new AzureLoadBalancerChoiceModal({
      application,
      loadBalancer: { loadBalancerType: 'Azure Application Gateway' },
    } as any);

    expect(modal.state.selectedChoice).toBe(AzureLoadBalancerTypes[1]);
  });
});
