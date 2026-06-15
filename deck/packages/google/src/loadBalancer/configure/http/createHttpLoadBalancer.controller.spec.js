import { ApplicationModelBuilder } from '@spinnaker/core';

describe('Controller: gceCreateHttpLoadBalancerCtrl (pipeline mode)', function () {
  beforeEach(function () {
    this.serializedCommands = [
      {
        name: 'app-http',
        portRange: '443',
        certificate: null,
        certificateMap: 'map',
        ipAddress: null,
        subnet: null,
        urlMapName: 'app-http',
        credentials: 'test',
        region: 'global',
        loadBalancerType: 'HTTP',
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
    window.module(require('./createHttpLoadBalancer.controller').name, ($provide) => {
      $provide.factory('gceHttpLoadBalancerCommandBuilder', ($q) => ({
        buildCommand: () =>
          $q.when({
            loadBalancer: {
              listeners: [{ name: 'app-http', port: 443 }],
              credentials: 'test',
              region: 'global',
              urlMapName: 'app-http',
            },
          }),
      }));
      const wizardSubFormValidation = {
        config: () => wizardSubFormValidation,
        register: () => wizardSubFormValidation,
      };
      $provide.value('wizardSubFormValidation', wizardSubFormValidation);
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
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      this.ctrl = $controller('gceCreateHttpLoadBalancerCtrl', {
        $scope: this.$scope,
        $uibModal: {},
        $uibModalInstance: this.modalInstance,
        application: app,
        loadBalancer: null,
        isNew: true,
        forPipelineConfig: true,
        $state: {},
      });
      $rootScope.$digest();
    }),
  );

  it('returns pipeline commands without submitting', function () {
    this.ctrl.submit();

    expect(this.writer.upsertLoadBalancers).not.toHaveBeenCalled();
    expect(this.modalInstance.close).toHaveBeenCalledWith([
      jasmine.objectContaining({
        name: 'app-http',
        loadBalancerName: 'app-http',
        cloudProvider: 'gce',
        listeners: [
          jasmine.objectContaining({
            name: 'app-http',
            port: '443',
            certificateMap: 'map',
          }),
        ],
      }),
    ]);
  });
});
