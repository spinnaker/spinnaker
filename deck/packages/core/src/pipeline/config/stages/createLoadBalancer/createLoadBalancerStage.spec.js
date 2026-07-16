import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import { name as moduleName, openLoadBalancerModal } from './createLoadBalancerStage';

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

describe('createLoadBalancerStageCtrl', function () {
  beforeEach(() => {
    window.module(moduleName);
  });

  beforeEach(
    window.inject(function ($controller, $rootScope, $q) {
      this.$scope = $rootScope.$new();
      this.$scope.stage = {};
      this.$scope.application = {};
      this.modalResult = $q.when([{ name: 'lb1' }, { name: 'lb2' }]);
      this.cloudProviderConfig = {
        CreateLoadBalancerModal: {
          supportsPipelineConfig: true,
          show: jasmine.createSpy('show').and.callFake(() => this.modalResult),
        },
      };

      spyOn(ProviderSelectionService, 'selectProvider').and.returnValue($q.when('gce'));
      spyOn(CloudProviderRegistry, 'getValue').and.returnValue(this.cloudProviderConfig);

      this.ctrl = $controller('createLoadBalancerStageCtrl', {
        $scope: this.$scope,
        $uibModal: { open: jasmine.createSpy('open') },
      });
      this.$rootScope = $rootScope;
      this.$q = $q;
    }),
  );

  it('flattens array results when adding load balancers', function () {
    this.ctrl.addLoadBalancer();
    this.$rootScope.$digest();

    expect(this.$scope.stage.loadBalancers).toEqual([{ name: 'lb1' }, { name: 'lb2' }]);
  });

  it('splices array results when editing a load balancer', function () {
    this.$scope.stage.loadBalancers = [{ name: 'old' }];
    this.modalResult = this.$q.when([{ name: 'new1' }, { name: 'new2' }]);

    this.ctrl.editLoadBalancer(this.$scope.stage.loadBalancers[0], 0);
    this.$rootScope.$digest();

    expect(this.$scope.stage.loadBalancers).toEqual([{ name: 'new1' }, { name: 'new2' }]);
  });
});
