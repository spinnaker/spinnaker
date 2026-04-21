import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import { name as moduleName } from './createLoadBalancerStage';

describe('createLoadBalancerStageCtrl', function () {
  beforeEach(() => {
    window.module(moduleName);
  });

  beforeEach(
    window.inject(function ($controller, $rootScope, $q) {
      this.$scope = $rootScope.$new();
      this.$scope.stage = {};
      this.$scope.application = {};
      this.$uibModal = {
        open: jasmine.createSpy('open').and.returnValue({ result: $q.when([{ name: 'lb1' }, { name: 'lb2' }]) }),
      };

      spyOn(ProviderSelectionService, 'selectProvider').and.returnValue($q.when('gce'));
      this.cloudProviderConfig = {
        createLoadBalancerTemplateUrl: 'template',
        createLoadBalancerController: 'ctrl',
      };
      spyOn(CloudProviderRegistry, 'getValue').and.callFake(() => this.cloudProviderConfig);

      this.ctrl = $controller('createLoadBalancerStageCtrl', {
        $scope: this.$scope,
        $uibModal: this.$uibModal,
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
    this.$uibModal.open.and.returnValue({ result: this.$q.when([{ name: 'new1' }, { name: 'new2' }]) });

    this.ctrl.editLoadBalancer(this.$scope.stage.loadBalancers[0], 0);
    this.$rootScope.$digest();

    expect(this.$scope.stage.loadBalancers).toEqual([{ name: 'new1' }, { name: 'new2' }]);
  });

  it('uses provider hook when adding load balancers', function () {
    const hookResult = { name: 'hook-lb' };
    this.cloudProviderConfig.pipelineCreateLoadBalancerModal = jasmine
      .createSpy('pipelineCreateLoadBalancerModal')
      .and.returnValue(this.$q.when(hookResult));

    this.ctrl.addLoadBalancer();
    this.$rootScope.$digest();

    expect(this.cloudProviderConfig.pipelineCreateLoadBalancerModal).toHaveBeenCalledWith(
      jasmine.objectContaining({
        application: this.$scope.application,
        loadBalancer: null,
        isNew: true,
        $uibModal: this.$uibModal,
      }),
    );
    expect(this.$uibModal.open).not.toHaveBeenCalled();
    expect(this.$scope.stage.loadBalancers).toEqual([hookResult]);
  });

  it('uses provider hook when editing a load balancer', function () {
    this.$scope.stage.loadBalancers = [{ name: 'old' }];
    const hookResult = { name: 'updated' };
    this.cloudProviderConfig.pipelineCreateLoadBalancerModal = jasmine
      .createSpy('pipelineCreateLoadBalancerModal')
      .and.returnValue(this.$q.when(hookResult));

    this.ctrl.editLoadBalancer(this.$scope.stage.loadBalancers[0], 0);
    this.$rootScope.$digest();

    const hookArgs = this.cloudProviderConfig.pipelineCreateLoadBalancerModal.calls.mostRecent().args[0];
    expect(hookArgs.loadBalancer).toEqual({ name: 'old' });
    expect(hookArgs.loadBalancer).not.toBe(this.$scope.stage.loadBalancers[0]);
    expect(hookArgs.isNew).toBe(false);
    expect(this.$uibModal.open).not.toHaveBeenCalled();
    expect(this.$scope.stage.loadBalancers).toEqual([hookResult]);
  });

  it('flattens array results when hook returns array', function () {
    this.cloudProviderConfig.pipelineCreateLoadBalancerModal = jasmine
      .createSpy('pipelineCreateLoadBalancerModal')
      .and.returnValue(this.$q.when([{ name: 'hook1' }, { name: 'hook2' }]));

    this.ctrl.addLoadBalancer();
    this.$rootScope.$digest();

    expect(this.$uibModal.open).not.toHaveBeenCalled();
    expect(this.$scope.stage.loadBalancers).toEqual([{ name: 'hook1' }, { name: 'hook2' }]);
  });
});
