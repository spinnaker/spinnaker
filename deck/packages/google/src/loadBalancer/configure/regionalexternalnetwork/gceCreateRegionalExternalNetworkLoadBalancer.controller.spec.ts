import { AccountService, ApplicationModelBuilder, LoadBalancerWriter } from '@spinnaker/core';

import { GCE_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER_CTRL } from './gceCreateRegionalExternalNetworkLoadBalancer.controller';

describe('Controller: gceRegionalExternalNetworkLoadBalancerCtrl', function () {
  beforeEach(function () {
    window.module(GCE_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER_CTRL, ($provide: ng.auto.IProvideService) => {
      this.backingData = {
        accounts: [{ name: 'test-account' }],
        existingLoadBalancerNamesByAccount: { 'test-account': [] },
        healthChecks: [],
      };
      this.commandBuilder = {
        getBackingData: jasmine.createSpy('getBackingData'),
        groupHealthChecksByAccountAndType: jasmine.createSpy('groupHealthChecksByAccountAndType').and.returnValue({}),
        groupHealthCheckNamesByAccount: jasmine.createSpy('groupHealthCheckNamesByAccount').and.returnValue({ 'test-account': [] }),
      };
      this.addressReader = {
        listAddresses: jasmine.createSpy('listAddresses'),
      };
      $provide.value('gceCommonLoadBalancerCommandBuilder', this.commandBuilder);
      $provide.value('gceAddressReader', this.addressReader);
    });
  });

  beforeEach(
    window.inject(function ($controller: ng.IControllerService, $rootScope: ng.IRootScopeService, $q: ng.IQService) {
      this.$controller = $controller;
      this.$scope = $rootScope.$new();
      this.$q = $q;
      this.commandBuilder.getBackingData.and.returnValue($q.when(this.backingData));
      this.addressReader.listAddresses.and.returnValue(
        $q.when([
          { address: '35.1.2.3', addressType: 'EXTERNAL', networkTier: 'PREMIUM' },
          { address: '10.0.0.1', addressType: 'INTERNAL', networkTier: 'PREMIUM' },
        ]),
      );
      spyOn(AccountService, 'getRegionsForAccount').and.returnValue($q.when([{ name: 'us-central1' }]));
      this.modalInstance = {
        close: jasmine.createSpy('close'),
        dismiss: jasmine.createSpy('dismiss'),
        result: { then: () => {} },
      };
      this.wizardSubFormValidation = {
        config: () => this.wizardSubFormValidation,
        register: () => this.wizardSubFormValidation,
      };
      this.app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
    }),
  );

  function createController(testContext: any, forPipelineConfig = true): any {
    const ctrl = testContext.$controller('gceRegionalExternalNetworkLoadBalancerCtrl', {
      $scope: testContext.$scope,
      application: testContext.app,
      $uibModalInstance: testContext.modalInstance,
      loadBalancer: {},
      gceCommonLoadBalancerCommandBuilder: testContext.commandBuilder,
      gceAddressReader: testContext.addressReader,
      isNew: true,
      forPipelineConfig,
      wizardSubFormValidation: testContext.wizardSubFormValidation,
      $state: {},
    });
    ctrl.$onInit();
    testContext.$scope.$digest();
    testContext.$scope.$digest();
    return ctrl;
  }

  it('keeps UDP session affinity selectable', function () {
    const ctrl = createController(this);

    ctrl.loadBalancer.ipProtocol = 'UDP';
    ctrl.viewState.sessionAffinity = 'Client IP and protocol';
    ctrl.protocolUpdated();
    ctrl.setSessionAffinity(ctrl.viewState);

    expect(ctrl.loadBalancer.backendService.sessionAffinity).toBe('CLIENT_IP_PROTO');
  });

  it('filters external addresses and stores selected IP and network tier', function () {
    const ctrl = createController(this);

    expect(ctrl.addresses).toEqual([{ address: '35.1.2.3', addressType: 'EXTERNAL', networkTier: 'PREMIUM' }]);

    ctrl.onAddressSelect({ address: '35.1.2.3', addressType: 'EXTERNAL', networkTier: 'PREMIUM' });

    expect(ctrl.loadBalancer.ipAddress).toBe('35.1.2.3');
    expect(ctrl.loadBalancer.networkTier).toBe('PREMIUM');
  });

  it('closes with a regional external network pipeline command instead of submitting', function () {
    spyOn(LoadBalancerWriter, 'upsertLoadBalancer');
    const ctrl = createController(this);
    ctrl.loadBalancer.ports = '80, 443';
    ctrl.loadBalancer.backendService.healthCheck = { name: 'tcp-hc', healthCheckType: 'TCP', port: 80 };

    ctrl.submit();

    expect(LoadBalancerWriter.upsertLoadBalancer).not.toHaveBeenCalled();
    expect(this.modalInstance.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        cloudProvider: 'gce',
        name: 'app',
        loadBalancerName: 'app',
        loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
        ports: ['80', '443'],
        backendService: jasmine.objectContaining({
          name: 'app',
          healthCheck: jasmine.objectContaining({ name: 'tcp-hc' }),
        }),
      }),
    );
  });
});
