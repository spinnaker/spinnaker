import { mockHttpClient } from 'core/api/mock/jasmine';
import { IControllerService, IRootScopeService, IScope, mock, noop } from 'angular';
import { StateService } from '@uirouter/core';

import { ApplicationModelBuilder } from '@spinnaker/core';

import { ORACLE_LOAD_BALANCER_CREATE_CONTROLLER, OracleLoadBalancerController } from './createLoadBalancer.controller';
import {
  IOracleLoadBalancer,
  IOracleLoadBalancerUpsertCommand,
  LoadBalancingPolicy,
} from '../../domain/IOracleLoadBalancer';
import { OracleProviderSettings } from '../../oracle.settings';

describe('Controller: oracleCreateLoadBalancerCtrl', function () {
  let controller: OracleLoadBalancerController;
  let initController: Function;
  const loadBalancer: IOracleLoadBalancer = null;
  let $scope: IScope;
  let $state: StateService;

  beforeEach(mock.module(ORACLE_LOAD_BALANCER_CREATE_CONTROLLER));

  // Initialize the controller and a mock scope
  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$state_: StateService) => {
      $scope = $rootScope.$new();
      $state = _$state_;
      const application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });

      const isNew = true;
      initController = () => {
        controller = $controller(OracleLoadBalancerController, {
          $scope,
          $uibModalInstance: { dismiss: noop, result: { then: noop } },
          loadBalancer,
          application,
          $state,
          isNew,
        });
        controller.addBackendSet();
        controller.addListener();
        controller.addCert();

        controller.listeners[0].defaultBackendSetName = controller.backendSets[0].name;
      };
    }),
  );

  function initListenerSslConfig() {
    controller.listeners[0].isSsl = true;
    controller.listenerIsSslChanged(controller.listeners[0]);
  }

  it('should have an instantiated controller', function () {
    initController();
    expect(controller).toBeDefined();
  });

  it('correctly creates a default loadbalancer', function () {
    initController();
    const lb: IOracleLoadBalancerUpsertCommand = $scope.loadBalancerCmd;
    expect(lb.cloudProvider).toEqual('oracle');
    expect(lb.credentials).toEqual(OracleProviderSettings.defaults.account);
    expect(lb.region).toEqual(OracleProviderSettings.defaults.region);
    expect(lb.isPrivate).toEqual(false);
    expect($scope.existingLoadBalancerNames).toEqual(undefined);
  });

  it('correctly creates default listener', function () {
    initController();
    expect(controller.listeners).toBeDefined();
    expect(controller.listeners.length).toEqual(1);
    expect(controller.listeners[0].name).toEqual('HTTP_80');
    expect(controller.listeners[0].protocol).toEqual('HTTP');
    expect(controller.listeners[0].port).toEqual(80);
  });

  it('correctly creates default subnet', function () {
    initController();
    expect(controller.backendSets).toBeDefined();
    expect(controller.backendSets.length).toEqual(1);
    expect(controller.backendSets[0].name).toEqual('backendSet1');
    expect(controller.backendSets[0].policy).toEqual(LoadBalancingPolicy.ROUND_ROBIN);
    expect(controller.backendSets[0].healthChecker.protocol).toEqual('HTTP');
    expect(controller.backendSets[0].healthChecker.port).toEqual(80);
    expect(controller.backendSets[0].healthChecker.urlPath).toEqual('/');
  });

  it('correctly creates default certificate', function () {
    initController();
    expect(controller.certificates).toBeDefined();
    expect(controller.certificates.length).toEqual(1);
    expect(controller.certificates[0].certificateName).toEqual('certificate1');
  });

  it('adds & removes certificate', function () {
    initController();
    controller.addCert();
    expect(controller.certificates).toBeDefined();
    expect(controller.certificates.length).toEqual(2);
    expect(controller.certificates[1].certificateName).toEqual('certificate2');
    controller.removeCert(0);
    expect(controller.certificates.length).toEqual(1);
    expect(controller.certificates[0].certificateName).toEqual('certificate2');
  });

  it('cannot remove certificate if used by listener', function () {
    initController();
    const newCertName = 'myCert';
    controller.addCert();
    expect(controller.certificates.length).toEqual(2);
    controller.certificates[1].certificateName = newCertName;
    controller.certNameChanged(1);
    controller.addListener();
    controller.listeners[1].isSsl = true;
    controller.listeners[1].sslConfiguration = {
      certificateName: newCertName,
      verifyDepth: 0,
      verifyPeerCertificates: false,
    };
    expect(controller.isCertRemovable(1)).toEqual(false);
    controller.removeListener(1);
    expect(controller.isCertRemovable(1)).toEqual(true);
    controller.removeCert(1);
  });

  it('changed backend set name updates listener', function () {
    initController();
    expect(controller.listeners[0].defaultBackendSetName).toEqual('backendSet1');
    controller.backendSets[0].name = 'UpdatedBackendSetName';
    controller.backendSetNameChanged(0);
    expect(controller.listeners[0].defaultBackendSetName).toEqual('UpdatedBackendSetName');
  });

  it('cannot remove backendset if used by listener', function () {
    initController();
    const newBackendSetName = 'myBackendSet';
    controller.addBackendSet();
    controller.addListener();
    controller.backendSets[1].name = newBackendSetName;
    controller.backendSetNameChanged(1);
    controller.listeners[1].defaultBackendSetName = newBackendSetName;
    expect(controller.isBackendSetRemovable(1)).toEqual(false);
    controller.removeListener(1);
    expect(controller.isBackendSetRemovable(1)).toEqual(true);
    controller.removeBackendSet(1);
  });

  it('remove backend set updates listener', function () {
    initController();
    controller.removeBackendSet(0);
    expect(controller.listeners[0].defaultBackendSetName).not.toBeDefined();
  });

  it('sslConfiguration created on listener', function () {
    initController();
    expect(controller.listeners[0].sslConfiguration).not.toBeDefined();
    initListenerSslConfig();
    expect(controller.listeners[0].sslConfiguration).toBeDefined();
  });

  it('sslConfiguration removed on listener when isSsl turned off', function () {
    initController();
    initListenerSslConfig();
    expect(controller.listeners[0].sslConfiguration).toBeDefined();
    controller.listeners[0].isSsl = false;
    controller.listenerIsSslChanged(controller.listeners[0]);
    expect(controller.listeners[0].sslConfiguration).not.toBeDefined();
  });

  it('changed certificate name updates listener', function () {
    initController();
    initListenerSslConfig();
    controller.listeners[0].sslConfiguration.certificateName = controller.certificates[0].certificateName;
    expect(controller.listeners[0].sslConfiguration.certificateName).toEqual('certificate1');
    controller.certificates[0].certificateName = 'someOtherCertName';
    controller.certNameChanged(0);
    expect(controller.listeners[0].sslConfiguration.certificateName).toEqual('someOtherCertName');
  });

  it('remove certificate updates listener', function () {
    initController();
    initListenerSslConfig();
    controller.listeners[0].sslConfiguration.certificateName = controller.certificates[0].certificateName;
    controller.removeCert(0);
    expect(controller.listeners[0].sslConfiguration.certificateName).not.toBeDefined();
  });

  it('makes the expected REST calls for data for a new loadbalancer', async function () {
    const http = mockHttpClient();
    http.expectGET('/networks/oracle').respond(200, []);
    http.expectGET('/subnets/oracle').respond(200, []);
    initController();

    await http.flush();
  });
});
