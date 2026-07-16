import { openLoadBalancerModal } from './createLoadBalancerStage';

describe('createLoadBalancerStage modal opener', () => {
  it('opens a React load balancer modal when the provider has one', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const result = { name: 'fnord-main', type: 'upsertLoadBalancer' };
    const config = {
      CreateLoadBalancerModal: {
        supportsPipelineConfig: true,
        show: jasmine.createSpy('show').and.returnValue(Promise.resolve(result)),
      },
    };
    const modalService = { open: jasmine.createSpy('open') };

    const command = await openLoadBalancerModal(config, modalService, {
      application,
      loadBalancer,
      isNew: false,
      forPipelineConfig: true,
    });

    expect(command).toBe(result);
    expect(config.CreateLoadBalancerModal.show).toHaveBeenCalledWith({
      app: application,
      application,
      loadBalancer,
      isNew: false,
      forPipelineConfig: true,
    });
    expect(modalService.open).not.toHaveBeenCalled();
  });

  it('falls back to the Angular modal registration when a React modal does not support pipeline results', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const result = { name: 'fnord-main', type: 'upsertLoadBalancer' };
    const config = {
      CreateLoadBalancerModal: {
        show: jasmine.createSpy('show').and.returnValue(Promise.resolve(result)),
      },
      createLoadBalancerController: 'legacyCtrl',
      createLoadBalancerTemplateUrl: 'legacy.html',
    };
    const modalService = { open: jasmine.createSpy('open').and.returnValue({ result: Promise.resolve(result) }) };

    const command = await openLoadBalancerModal(config, modalService, {
      application,
      loadBalancer,
      isNew: false,
      forPipelineConfig: true,
    });

    expect(command).toBe(result);
    expect(config.CreateLoadBalancerModal.show).not.toHaveBeenCalled();
    expect(modalService.open).toHaveBeenCalledWith(
      jasmine.objectContaining({
        templateUrl: 'legacy.html',
        controller: 'legacyCtrl as ctrl',
      }),
    );
  });

  it('falls back to the Angular modal registration for legacy providers', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const result = { name: 'fnord-main', type: 'upsertLoadBalancer' };
    const config = {
      createLoadBalancerController: 'legacyCtrl',
      createLoadBalancerTemplateUrl: 'legacy.html',
    };
    const modalService = { open: jasmine.createSpy('open').and.returnValue({ result: Promise.resolve(result) }) };

    const command = await openLoadBalancerModal(config, modalService, {
      application,
      loadBalancer,
      isNew: false,
      forPipelineConfig: true,
    });

    expect(command).toBe(result);
    expect(modalService.open).toHaveBeenCalledWith({
      templateUrl: 'legacy.html',
      controller: 'legacyCtrl as ctrl',
      size: 'lg',
      resolve: {
        application: jasmine.any(Function),
        loadBalancer: jasmine.any(Function),
        isNew: jasmine.any(Function),
        forPipelineConfig: jasmine.any(Function),
      },
    });
    const modalOptions = modalService.open.calls.mostRecent().args[0];
    expect(modalOptions.resolve.application()).toBe(application);
    expect(modalOptions.resolve.loadBalancer()).toBe(loadBalancer);
    expect(modalOptions.resolve.isNew()).toBe(false);
    expect(modalOptions.resolve.forPipelineConfig()).toBe(true);
  });
});
