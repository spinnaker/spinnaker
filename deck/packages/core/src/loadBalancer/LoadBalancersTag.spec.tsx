import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { LoadBalancersTag } from './LoadBalancersTag';
import type { ILoadBalancersTagProps } from './LoadBalancersTagWrapper';
import type { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import type { IServerGroup } from '../domain';
import { HoverablePopover } from '../presentation';

describe('<LoadBalancersTag />', () => {
  const lb1 = { name: 'lb1', account: 'prod', region: 'us-east-1', vpcId: 'vpc-1' };
  const lb2 = { name: 'lb2', account: 'prod', region: 'us-east-1' };

  let application: Application, component: ReactWrapper<any, any>, router: UIRouterReact;

  beforeEach(async () => {
    router = new UIRouterReact();
    application = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'loadBalancers',
      loader: () => Promise.resolve(application.loadBalancers.data),
      onLoad: (_app, data) => Promise.resolve(data),
      defaultData: [],
    });
    await application.loadBalancers.refresh();
  });

  afterEach(() => {
    component?.unmount();
    router.dispose();
  });

  const mountTag = (props: ILoadBalancersTagProps) =>
    mount(
      <UIRouterContext.Provider value={router}>
        <LoadBalancersTag {...props} />
      </UIRouterContext.Provider>,
    );

  it('extracts single load balancer from data', async () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1'],
      instances: [],
    } as IServerGroup;

    application.getDataSource('loadBalancers').data = [lb1, lb2];

    const props: ILoadBalancersTagProps = { application, serverGroup };
    component = mountTag(props);

    await Promise.resolve();
    await Promise.resolve();
    component.update();
    expect(component.render().find('span.btn-load-balancer').length).toBe(1);
  });

  it('extracts two load balancers from data', async () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1', 'lb2'],
      instances: [],
    } as IServerGroup;

    application.getDataSource('loadBalancers').data = [lb1, lb2];

    const props: ILoadBalancersTagProps = { application, serverGroup };
    const popoverContainerEl = document.createElement('div');
    component = mountTag({ ...props, container: popoverContainerEl });

    await act(async () => {
      await Promise.resolve();
    });
    component.update();
    const popover = component.find(HoverablePopover);
    expect(popover.length).toBe(1);

    await act(
      () =>
        new Promise<void>((resolve) => {
          popover.instance().setState({ popoverIsOpen: true, animation: false }, resolve);
        }),
    );
    const menuChildren = popoverContainerEl.querySelector('.popover-content div.menu-load-balancers').children;

    expect(menuChildren.length).toBe(3);
    expect(menuChildren[0].textContent.trim()).toBe('Load Balancers');
    expect(menuChildren[1].textContent.trim()).toBe('lb1');
    expect(menuChildren[2].textContent.trim()).toBe('lb2');
  });
});
