import { IControllerService, IRootScopeService, mock } from 'angular';
import { StateService } from '@uirouter/core';

import { ApplicationModelBuilder, ISubnet } from '@spinnaker/core';

import { AWS_LOAD_BALANCER_DETAILS_CTRL, AwsLoadBalancerDetailsController } from './loadBalancerDetails.controller';

describe('Controller: LoadBalancerDetailsCtrl', function () {
  let controller: AwsLoadBalancerDetailsController;
  let $scope;
  let $state;
  const loadBalancer = {
    name: 'foo',
    region: 'us-west-1',
    account: 'test',
    accountId: 'test',
    vpcId: '1',
  };

  beforeEach(mock.module(AWS_LOAD_BALANCER_DETAILS_CTRL));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$state_: StateService) => {
      $scope = $rootScope.$new();
      $state = _$state_;
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      app.loadBalancers.data.push(loadBalancer);
      controller = $controller(AwsLoadBalancerDetailsController, {
        $scope,
        loadBalancer,
        app,
        $state,
      });
    }),
  );

  it('should have an instantiated controller', function () {
    expect(controller).toBeDefined();
  });

  describe('Get the first subnets purpose', function () {
    it('should return empty string if there are no subnets ', function () {
      const subnetDetails: ISubnet[] = [];
      const result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('');
    });

    it('should return empty string if no subnetDetail is submitted', function () {
      const result = controller.getFirstSubnetPurpose();
      expect(result).toEqual('');
    });

    it('should return empty string if undefined subnetDetail is submitted', function () {
      const result = controller.getFirstSubnetPurpose(undefined);
      expect(result).toEqual('');
    });

    it('should return the first purpose of subnetDetail if there is only one', function () {
      const subnetDetails = [{ purpose: 'internal(vpc0)' }] as ISubnet[];
      const result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('internal(vpc0)');
    });

    it('should return the first purpose of subnetDetail if there are multiple', function () {
      const subnetDetails = [{ purpose: 'internal(vpc0)' }, { purpose: 'internal(vpc1)' }] as ISubnet[];
      const result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('internal(vpc0)');
    });
  });
});
