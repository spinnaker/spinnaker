import { ApplicationModelBuilder } from '@spinnaker/core';
import { GOOGLE_LOADBALANCER_CONFIGURE_EXTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER } from './createExternalHttpLoadBalancer.controller';

describe('Controller: gceCreateExternalHttpLoadBalancerCtrl (pipeline mode)', function () {
  beforeEach(function () {
    this.serializedCommands = [
      {
        name: 'app-external-http',
        portRange: '443',
        certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert-1',
        certificateMap: null,
        ipAddress: '34.0.0.1',
        networkTier: 'STANDARD',
        subnet: null,
        urlMapName: 'app-external-http',
        credentials: 'test',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
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
    window.module(GOOGLE_LOADBALANCER_CONFIGURE_EXTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER, ($provide) => {
      $provide.value('gceHttpLoadBalancerCommandBuilder', {});
      $provide.value('gceHttpLoadBalancerTransformer', this.transformer);
      $provide.value('gceHttpLoadBalancerWriter', this.writer);
    });
  });

  beforeEach(
    window.inject(function ($controller, $rootScope) {
      this.$controller = $controller;
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
      this.app = app;
      this.ctrl = $controller('gceCreateExternalHttpLoadBalancerCtrl', {
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

  it('returns regional external pipeline commands without submitting', function () {
    this.ctrl.submit();

    expect(this.writer.upsertLoadBalancers).not.toHaveBeenCalled();
    expect(this.modalInstance.close).toHaveBeenCalledWith([
      jasmine.objectContaining({
        name: 'app-external-http',
        loadBalancerName: 'app-external-http',
        cloudProvider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        listeners: [
          jasmine.objectContaining({
            name: 'app-external-http',
            port: '443',
            certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert-1',
            networkTier: 'STANDARD',
          }),
        ],
      }),
    ]);
  });

  it('submits regional external commands in normal mode', function () {
    this.writer.upsertLoadBalancers.and.returnValue(Promise.resolve());
    const ctrl = this.$controller('gceCreateExternalHttpLoadBalancerCtrl', {
      $scope: this.$scope,
      application: this.app,
      $uibModalInstance: this.modalInstance,
      loadBalancer: {},
      gceHttpLoadBalancerCommandBuilder: {},
      isNew: true,
      forPipelineConfig: false,
      wizardSubFormValidation: this.wizardSubFormValidation,
      gceHttpLoadBalancerTransformer: this.transformer,
      gceHttpLoadBalancerWriter: this.writer,
      $state: {},
    });
    ctrl.command = {};

    ctrl.submit();

    expect(this.modalInstance.close).not.toHaveBeenCalled();
    expect(this.writer.upsertLoadBalancers).toHaveBeenCalledWith(this.serializedCommands, this.app, 'Create');
  });
});
