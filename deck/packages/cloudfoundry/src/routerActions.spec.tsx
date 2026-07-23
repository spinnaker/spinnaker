import { shallow } from 'enzyme';
import React from 'react';

import { ConfirmationModalService, ServerGroupWarningMessageService } from '@spinnaker/core';

import { CloudFoundryInstanceActionsComponent } from './instance/details/CloudFoundryInstanceActions';
import { CloudFoundryLoadBalancerActionsComponent } from './loadBalancer/details/CloudFoundryLoadBalancerActions';
import { CloudFoundryServerGroupActionsComponent } from './serverGroup/details/cloudFoundryServerGroupActions';

describe('Cloud Foundry routed actions', () => {
  const routerProps = (includes: jasmine.Spy, go: jasmine.Spy) =>
    ({ router: {}, stateParams: {}, stateService: { go, includes } } as any);

  it('closes instance details through the injected state service', () => {
    const includes = jasmine.createSpy('includes').and.returnValue(true);
    const go = jasmine.createSpy('go');
    const confirm = spyOn(ConfirmationModalService, 'confirm');
    const component = shallow(
      <CloudFoundryInstanceActionsComponent
        {...routerProps(includes, go)}
        application={{} as any}
        instance={{ account: 'test', name: 'instance-id' } as any}
      />,
    );

    (component.instance() as any).terminateInstance();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('closes load balancer details through the injected state service', () => {
    const includes = jasmine.createSpy('includes').and.returnValue(true);
    const go = jasmine.createSpy('go');
    const confirm = spyOn(ConfirmationModalService, 'confirm');
    const component = shallow(
      <CloudFoundryLoadBalancerActionsComponent
        {...routerProps(includes, go)}
        application={{} as any}
        loadBalancer={{ account: 'test', name: 'route', region: 'test' } as any}
      />,
    );

    (component.instance() as any).deleteLoadBalancer();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('closes server group details through the injected state service', () => {
    const includes = jasmine.createSpy('includes').and.returnValue(true);
    const go = jasmine.createSpy('go');
    spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage');
    const confirm = spyOn(ConfirmationModalService, 'confirm');
    const component = shallow(
      <CloudFoundryServerGroupActionsComponent
        {...routerProps(includes, go)}
        app={{ attributes: {} } as any}
        serverGroup={
          {
            account: 'test',
            cloudProvider: 'cloudfoundry',
            moniker: { cluster: 'app' },
            name: 'app-v001',
            region: 'test',
          } as any
        }
      />,
    );

    (component.instance() as any).destroyServerGroup();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });
});
