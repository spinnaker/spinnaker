import {
  OracleLoadBalancerDetailController,
  ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER,
} from './loadBalancerDetail.controller';
import { IControllerService, IRootScopeService, IScope, mock } from 'angular';
import { StateService } from '@uirouter/angularjs';
import {
  ApplicationModelBuilder,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  CONFIRMATION_MODAL_SERVICE,
  ConfirmationModalService,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerReader,
} from '@spinnaker/core';

describe('Controller: oracleLoadBalancerDetailCtrl', function() {
  let controller: OracleLoadBalancerDetailController;
  let $scope: IScope;
  let $state: StateService;
  let securityGroupReader: SecurityGroupReader;
  let confirmationModalService: ConfirmationModalService;
  let loadBalancerReader: LoadBalancerReader;

  const loadBalancer = {
    name: 'foo',
    region: 'us-west-1',
    account: 'test',
    accountId: 'test',
    vpcId: '1',
  };

  beforeEach(
    mock.module(
      ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER,
      SECURITY_GROUP_READER,
      LOAD_BALANCER_READ_SERVICE,
      CONFIRMATION_MODAL_SERVICE,
    ),
  );

  beforeEach(
    mock.inject(
      (
        $controller: IControllerService,
        $rootScope: IRootScopeService,
        _$state_: StateService,
        _securityGroupReader_: SecurityGroupReader,
        _confirmationModalService_: ConfirmationModalService,
        _loadBalancerReader_: LoadBalancerReader,
      ) => {
        $scope = $rootScope.$new();
        $state = _$state_;
        const app = ApplicationModelBuilder.createApplicationForTests('app', { key: 'loadBalancers', lazy: true });
        app.loadBalancers.data.push(loadBalancer);
        securityGroupReader = _securityGroupReader_;
        confirmationModalService = _confirmationModalService_;
        loadBalancerReader = _loadBalancerReader_;
        controller = $controller('oracleLoadBalancerDetailCtrl', {
          $scope: $scope,
          loadBalancer: loadBalancer,
          app: app,
          $state: $state,
          securityGroupReader: securityGroupReader,
          loadBalancerReader: loadBalancerReader,
          confirmationModalService: confirmationModalService,
        });
      },
    ),
  );

  it('should have an instantiated controller', function() {
    expect(controller).toBeDefined();
  });
});
