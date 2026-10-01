import type { Mock } from 'vitest';
import { shallow } from 'enzyme';
import React from 'react';

import { ConfirmationModalService, ServerGroupWarningMessageService } from '@spinnaker/core';

import { CloudFoundryInstanceActionsComponent } from './instance/details/CloudFoundryInstanceActions';
import { CloudFoundryLoadBalancerActionsComponent } from './loadBalancer/details/CloudFoundryLoadBalancerActions';
import { CloudFoundryServerGroupActionsComponent } from './serverGroup/details/cloudFoundryServerGroupActions';

describe('Cloud Foundry routed actions', () => {
  const routerProps = (includes: Mock, go: Mock) =>
    ({ router: {}, stateParams: {}, stateService: { go, includes } } as any);

  it('closes instance details through the injected state service', () => {
    const includes = vi.fn().mockReturnValue(true);
    const go = vi.fn();
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    const component = shallow(
      <CloudFoundryInstanceActionsComponent
        {...routerProps(includes, go)}
        application={{} as any}
        instance={{ account: 'test', name: 'instance-id' } as any}
      />,
    );

    (component.instance() as any).terminateInstance();
    confirm.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('closes load balancer details through the injected state service', () => {
    const includes = vi.fn().mockReturnValue(true);
    const go = vi.fn();
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    const component = shallow(
      <CloudFoundryLoadBalancerActionsComponent
        {...routerProps(includes, go)}
        application={{} as any}
        loadBalancer={{ account: 'test', name: 'route', region: 'test' } as any}
      />,
    );

    (component.instance() as any).deleteLoadBalancer();
    confirm.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('closes server group details through the injected state service', () => {
    const includes = vi.fn().mockReturnValue(true);
    const go = vi.fn();
    vi.spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
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
    confirm.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });
});
