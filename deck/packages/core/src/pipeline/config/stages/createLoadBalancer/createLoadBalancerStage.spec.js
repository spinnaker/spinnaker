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
      spyOn(CloudProviderRegistry, 'getValue').and.returnValue({
        createLoadBalancerTemplateUrl: 'template',
        createLoadBalancerController: 'ctrl',
      });

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
});
