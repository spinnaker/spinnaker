import { TaskMonitor } from '@spinnaker/core';

import { CreateApplicationLoadBalancerComponent } from './loadBalancer/configure/application/CreateApplicationLoadBalancer';
import { CreateClassicLoadBalancerComponent } from './loadBalancer/configure/classic/CreateClassicLoadBalancer';
import { CreateNetworkLoadBalancerComponent } from './loadBalancer/configure/network/CreateNetworkLoadBalancer';
import { CreateLambdaFunctionComponent } from './function/CreateLambdaFunction';
import { AmazonSecurityGroupModalComponent } from './securityGroup/configure/AmazonSecurityGroupModal';
import { AmazonCloneServerGroupModalComponent } from './serverGroup/configure/wizard/AmazonCloneServerGroupModal';

describe('Amazon routed modal consumers', () => {
  beforeEach(() => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: jasmine.createSpy('dismiss'),
      result: Promise.resolve(),
    } as any);
  });

  function stateService(includedState: string) {
    return {
      go: jasmine.createSpy('go'),
      includes: jasmine.createSpy('includes').and.callFake((state: string) => state === includedState),
    };
  }

  [
    ['application', CreateApplicationLoadBalancerComponent],
    ['classic', CreateClassicLoadBalancerComponent],
    ['network', CreateNetworkLoadBalancerComponent],
  ].forEach(([type, Component]: [string, any]) => {
    it(`navigates from the ${type} load balancer modal through its injected state service`, () => {
      const state = stateService('**.loadBalancerDetails');
      const modal = Object.create(Component.prototype) as any;
      modal.props = { dismissModal: jasmine.createSpy('dismissModal'), stateService: state };
      modal.state = {};
      modal.setState = jasmine.createSpy('setState');

      modal.onApplicationRefresh({
        credentials: 'test-account',
        name: 'fnord-main',
        region: 'eu-west-1',
        vpcId: 'vpc-1',
      });

      expect(state.go).toHaveBeenCalledWith('^.loadBalancerDetails', {
        accountId: 'test-account',
        name: 'fnord-main',
        provider: 'aws',
        region: 'eu-west-1',
        vpcId: 'vpc-1',
      });
    });
  });

  it('navigates from the function modal through its injected state service', () => {
    const state = stateService('');
    const modal = Object.create(CreateLambdaFunctionComponent.prototype) as any;
    modal.props = { dismissModal: jasmine.createSpy('dismissModal'), stateService: state };
    modal.state = {};
    modal.setState = jasmine.createSpy('setState');

    modal.onApplicationRefresh({ credentials: 'test-account', name: 'fnord', region: 'eu-west-1', vpcId: 'vpc-1' });

    expect(state.go).toHaveBeenCalledWith('.functionDetails', {
      accountId: 'test-account',
      name: 'fnord',
      provider: 'aws',
      region: 'eu-west-1',
      vpcId: 'vpc-1',
    });
  });

  it('navigates from the security group modal through its injected state service', () => {
    const state = stateService('**.firewallDetails');
    const refresh = jasmine.createSpy('refresh');
    const closeModal = jasmine.createSpy('closeModal');
    const modal = new AmazonSecurityGroupModalComponent({
      app: { securityGroups: { refresh } },
      closeModal,
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'clone',
      stateService: state,
    } as any) as any;
    modal.state = {
      securityGroup: {
        credentials: 'test-account',
        name: 'fnord-firewall',
        region: 'eu-west-1',
        vpcId: 'vpc-1',
      },
    };

    modal.onTaskComplete();

    expect(refresh).toHaveBeenCalled();
    expect(closeModal).toHaveBeenCalled();
    expect(state.go).toHaveBeenCalledWith('^.firewallDetails', {
      accountId: 'test-account',
      name: 'fnord-firewall',
      provider: 'aws',
      region: 'eu-west-1',
      vpcId: 'vpc-1',
    });
  });

  it('navigates from the clone server group modal through its injected state service', () => {
    const state = stateService('**.clusters');
    const modal = new AmazonCloneServerGroupModalComponent({
      application: { name: 'fnord' },
      command: {
        credentials: 'test-account',
        region: 'eu-west-1',
        viewState: { requiresTemplateSelection: true },
      },
      dismissModal: jasmine.createSpy('dismissModal'),
      stateService: state,
    } as any) as any;
    modal.state = {
      taskMonitor: {
        task: {
          execution: {
            stages: [
              {
                context: { 'deploy.server.groups': { 'eu-west-1': 'fnord-main-v042' } },
                type: 'cloneServerGroup',
              },
            ],
          },
        },
      },
    };

    modal.onApplicationRefresh();

    expect(state.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test-account',
      provider: 'aws',
      region: 'eu-west-1',
      serverGroup: 'fnord-main-v042',
    });
  });
});
