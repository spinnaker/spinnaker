import { ApplicationModelBuilder } from '@spinnaker/core';
import { GCE_INTERNAL_LOAD_BALANCER_CTRL } from './internal/gceCreateInternalLoadBalancer.controller';
import { GCE_SSL_LOAD_BALANCER_CTRL } from './ssl/gceCreateSslLoadBalancer.controller';
import { GCE_TCP_LOAD_BALANCER_CTRL } from './tcp/gceCreateTcpLoadBalancer.controller';

function buildApp() {
  return ApplicationModelBuilder.createApplicationForTests('app', {
    key: 'loadBalancers',
    lazy: true,
    defaultData: [],
  });
}

describe('GCE L4 load balancer controllers (pipeline mode)', function () {
  describe('gceSslLoadBalancerCtrl', function () {
    beforeEach(() => {
      window.module(GCE_SSL_LOAD_BALANCER_CTRL);
    });

    it(
      'closes with a pipeline command',
      window.inject(function ($controller, $rootScope) {
        const modalInstance = { close: jasmine.createSpy('close'), dismiss: jasmine.createSpy('dismiss') };
        const loadBalancer = {
          loadBalancerName: 'ssl-lb',
          backendService: { healthCheck: { healthCheckType: 'TCP' } },
          instances: [],
          credentials: 'test',
          region: 'global',
        };

        const ctrl = $controller('gceSslLoadBalancerCtrl', {
          $scope: $rootScope.$new(),
          application: buildApp(),
          $uibModalInstance: modalInstance,
          loadBalancer,
          gceCommonLoadBalancerCommandBuilder: {},
          isNew: true,
          forPipelineConfig: true,
          wizardSubFormValidation: {},
          $state: {},
        });

        ctrl.submit();

        expect(modalInstance.close).toHaveBeenCalledWith(
          jasmine.objectContaining({
            loadBalancerName: 'ssl-lb',
            cloudProvider: 'gce',
            healthCheck: {},
          }),
        );
      }),
    );
  });

  describe('gceTcpLoadBalancerCtrl', function () {
    beforeEach(() => {
      window.module(GCE_TCP_LOAD_BALANCER_CTRL);
    });

    it(
      'closes with a pipeline command',
      window.inject(function ($controller, $rootScope) {
        const modalInstance = { close: jasmine.createSpy('close'), dismiss: jasmine.createSpy('dismiss') };
        const loadBalancer = {
          loadBalancerName: 'tcp-lb',
          backendService: { healthCheck: { healthCheckType: 'TCP' } },
          instances: [],
          credentials: 'test',
          region: 'global',
        };

        const ctrl = $controller('gceTcpLoadBalancerCtrl', {
          $scope: $rootScope.$new(),
          application: buildApp(),
          $uibModalInstance: modalInstance,
          loadBalancer,
          gceCommonLoadBalancerCommandBuilder: {},
          isNew: true,
          forPipelineConfig: true,
          wizardSubFormValidation: {},
          $state: {},
        });

        ctrl.submit();

        expect(modalInstance.close).toHaveBeenCalledWith(
          jasmine.objectContaining({
            loadBalancerName: 'tcp-lb',
            cloudProvider: 'gce',
            healthCheck: {},
          }),
        );
      }),
    );
  });

  describe('gceInternalLoadBalancerCtrl', function () {
    beforeEach(() => {
      window.module(GCE_INTERNAL_LOAD_BALANCER_CTRL);
    });

    it(
      'closes with a pipeline command',
      window.inject(function ($controller, $rootScope) {
        const modalInstance = { close: jasmine.createSpy('close'), dismiss: jasmine.createSpy('dismiss') };
        const loadBalancer = {
          loadBalancerName: 'internal-lb',
          backendService: { healthCheck: { healthCheckType: 'TCP' } },
          instances: [],
          ports: '80, 8080',
          credentials: 'test',
          region: 'us-west-2',
        };

        const ctrl = $controller('gceInternalLoadBalancerCtrl', {
          $scope: $rootScope.$new(),
          application: buildApp(),
          $uibModalInstance: modalInstance,
          loadBalancer,
          gceCommonLoadBalancerCommandBuilder: {},
          isNew: true,
          forPipelineConfig: true,
          wizardSubFormValidation: {},
          gceXpnNamingService: {},
          $state: {},
        });

        ctrl.submit();

        expect(modalInstance.close).toHaveBeenCalledWith(
          jasmine.objectContaining({
            loadBalancerName: 'internal-lb',
            cloudProvider: 'gce',
            healthCheck: {},
            ports: ['80', '8080'],
          }),
        );
      }),
    );
  });
});
