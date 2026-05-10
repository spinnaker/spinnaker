import { ApplicationModelBuilder } from '@spinnaker/core';
import { GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER } from './createInternalHttpLoadBalancer.controller';

describe('Controller: gceCreateInternalHttpLoadBalancerCtrl (pipeline mode)', function () {
  beforeEach(function () {
    this.serializedCommands = [
      {
        name: 'app-internal-http',
        portRange: '443',
        certificate: null,
        certificateMap: null,
        ipAddress: '10.0.0.1',
        subnet: 'subnet-1',
        urlMapName: 'app-internal-http',
        credentials: 'test',
        region: 'us-west-2',
        loadBalancerType: 'INTERNAL_MANAGED',
      },
    ];
    this.transformer = {
      serialize: jasmine.createSpy('serialize').and.returnValue(this.serializedCommands),
    };
    this.writer = {
      upsertLoadBalancers: jasmine.createSpy('upsertLoadBalancers'),
    };
  });

  beforeEach(function () {
    window.module(GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER, ($provide) => {
      $provide.value('gceHttpLoadBalancerCommandBuilder', {});
      $provide.value('gceHttpLoadBalancerTransformer', this.transformer);
      $provide.value('gceHttpLoadBalancerWriter', this.writer);
    });
  });

  beforeEach(
    window.inject(function ($controller, $rootScope) {
      this.$scope = $rootScope.$new();
      this.modalInstance = {
        close: jasmine.createSpy('close'),
        dismiss: jasmine.createSpy('dismiss'),
        result: { then: () => {} },
      };
      this.wizardSubFormValidation = {
        config: () => this.wizardSubFormValidation,
        register: () => this.wizardSubFormValidation,
      };
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      this.ctrl = $controller('gceCreateInternalHttpLoadBalancerCtrl', {
        $scope: this.$scope,
        application: app,
        $uibModalInstance: this.modalInstance,
        loadBalancer: {},
        gceHttpLoadBalancerCommandBuilder: {},
        isNew: true,
        forPipelineConfig: true,
        wizardSubFormValidation: this.wizardSubFormValidation,
        gceHttpLoadBalancerTransformer: this.transformer,
        gceHttpLoadBalancerWriter: this.writer,
        $state: {},
      });
      this.ctrl.command = {};
    }),
  );

  it('returns pipeline commands without submitting', function () {
    this.ctrl.submit();

    expect(this.writer.upsertLoadBalancers).not.toHaveBeenCalled();
    expect(this.modalInstance.close).toHaveBeenCalledWith([
      jasmine.objectContaining({
        name: 'app-internal-http',
        loadBalancerName: 'app-internal-http',
        cloudProvider: 'gce',
        listeners: [
          jasmine.objectContaining({
            name: 'app-internal-http',
            port: '443',
            subnet: 'subnet-1',
          }),
        ],
      }),
    ]);
  });
});
