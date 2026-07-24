import {
  hasPipelineLoadBalancerModal,
  openLoadBalancerModal,
  upsertLoadBalancersStage,
} from './createLoadBalancerStage';

describe('createLoadBalancerStage registration', () => {
  it('registers create load balancers with direct React config and execution details', () => {
    expect(upsertLoadBalancersStage.key).toBe('upsertLoadBalancers');
    expect(upsertLoadBalancersStage.label).toBe('Create Load Balancers');
    expect(upsertLoadBalancersStage.description).toBe(
      'Creates one or more load balancers. If a load balancer exists with the same name, then that will be updated.',
    );
    expect(upsertLoadBalancersStage.component).toBeDefined();
    expect(upsertLoadBalancersStage.templateUrl).toBeUndefined();
    expect(upsertLoadBalancersStage.executionDetailsSections).toBeDefined();
    expect(upsertLoadBalancersStage.executionDetailsSections.map((section) => section.title)).toEqual([
      'loadBalancerConfig',
      'taskStatus',
    ]);
    expect(upsertLoadBalancersStage.supportsCustomTimeout).toBe(true);
    expect(upsertLoadBalancersStage.validators).toEqual([]);
  });
});

describe('createLoadBalancerStage modal opener', () => {
  it('only offers providers with React load balancer modals that support pipeline config', () => {
    expect(
      hasPipelineLoadBalancerModal({
        loadBalancer: { CreateLoadBalancerModal: { supportsPipelineConfig: true } },
      }),
    ).toBe(true);
    expect(
      hasPipelineLoadBalancerModal({
        loadBalancer: { CreateLoadBalancerModal: { supportsPipelineConfig: false } },
      }),
    ).toBe(false);
    expect(hasPipelineLoadBalancerModal({ loadBalancer: { CreateLoadBalancerModal: {} } })).toBe(false);
    expect(hasPipelineLoadBalancerModal({ loadBalancer: {} })).toBe(false);
  });

  it('opens a React load balancer modal when the provider has one', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const result = { name: 'fnord-main', type: 'upsertLoadBalancer' };
    const runtimeServices = {};
    const config = {
      CreateLoadBalancerModal: {
        supportsPipelineConfig: true,
        show: jasmine.createSpy('show').and.returnValue(Promise.resolve(result)),
      },
    };
    const command = await openLoadBalancerModal(
      config,
      {
        application,
        loadBalancer,
        isNew: false,
        forPipelineConfig: true,
      },
      runtimeServices,
    );

    expect(command).toBe(result);
    expect(config.CreateLoadBalancerModal.show).toHaveBeenCalledWith(
      {
        app: application,
        application,
        loadBalancer,
        isNew: false,
        forPipelineConfig: true,
      },
      runtimeServices,
    );
  });

  it('rejects when a React modal does not support pipeline results', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const result = { name: 'fnord-main', type: 'upsertLoadBalancer' };
    const config = {
      CreateLoadBalancerModal: {
        show: jasmine.createSpy('show').and.returnValue(Promise.resolve(result)),
      },
    };

    await expectAsync(
      openLoadBalancerModal(config, {
        application,
        loadBalancer,
        isNew: false,
        forPipelineConfig: true,
      }),
    ).toBeRejectedWithError('No React create load balancer modal is registered with pipeline support.');

    expect(config.CreateLoadBalancerModal.show).not.toHaveBeenCalled();
  });

  it('rejects when no React modal is registered', async () => {
    const application = { name: 'fnord' };
    const loadBalancer = { name: 'fnord-main' };
    const config = {};

    await expectAsync(
      openLoadBalancerModal(config, {
        application,
        loadBalancer,
        isNew: false,
        forPipelineConfig: true,
      }),
    ).toBeRejectedWithError('No React create load balancer modal is registered with pipeline support.');
  });
});
