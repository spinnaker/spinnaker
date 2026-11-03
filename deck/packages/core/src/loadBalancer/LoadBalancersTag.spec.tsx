import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { LoadBalancersTag } from './LoadBalancersTag';
import type { ILoadBalancersTagProps } from './LoadBalancersTagWrapper';
import type { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import type { IServerGroup } from '../domain';

describe('<LoadBalancersTag />', () => {
  const lb1 = { name: 'lb1', account: 'prod', region: 'us-east-1', vpcId: 'vpc-1' };
  const lb2 = { name: 'lb2', account: 'prod', region: 'us-east-1' };

  let application: Application, router: UIRouterReact;
  const popoverContainers: HTMLElement[] = [];

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
    popoverContainers.splice(0).forEach((container) => container.remove());
    router.dispose();
  });

  const renderTag = (props: ILoadBalancersTagProps) =>
    render(
      <UIRouterContext.Provider value={router}>
        <LoadBalancersTag {...props} />
      </UIRouterContext.Provider>,
    );

  const renderMultipleLoadBalancers = () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1', 'lb2'],
      instances: [],
    } as IServerGroup;
    application.getDataSource('loadBalancers').data = [lb1, lb2];
    const popoverContainer = document.createElement('div');
    popoverContainer.className = 'test-popover-container';
    popoverContainers.push(popoverContainer);
    const rendered = renderTag({ application, serverGroup, container: popoverContainer });
    document.body.appendChild(popoverContainer);
    return { ...rendered, popoverContainer };
  };

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
    const { container } = renderTag(props);

    await waitFor(() => expect(container.querySelectorAll('span.btn-load-balancer')).toHaveLength(1));
  });

  it('extracts two load balancers from data', async () => {
    const { container, popoverContainer } = renderMultipleLoadBalancers();

    const control = await screen.findByRole('button', { name: '2 load balancers' });
    const popoverTarget = control.closest('.HoverablePopover');
    expect(popoverTarget).not.toBeNull();

    fireEvent.mouseEnter(popoverTarget as HTMLElement);
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 110));
    });
    const menu = await within(popoverContainer).findByText('Load Balancers');

    const menuElement = menu.closest('.menu-load-balancers') as HTMLElement;
    expect(within(menuElement).getByText('lb1')).toBeTruthy();
    expect(within(menuElement).getByText('lb2')).toBeTruthy();
    expect(container.querySelector('.load-balancers-tag')).toHaveClass('overflowing');
  });

  it('opens from keyboard focus and closes with Escape', async () => {
    const user = userEvent.setup();
    const { popoverContainer } = renderMultipleLoadBalancers();

    await user.tab();
    const control = await screen.findByRole('button', { name: '2 load balancers' });
    expect(control).toHaveFocus();
    expect(await within(popoverContainer).findByText('Load Balancers')).toBeInTheDocument();

    await user.keyboard('{Enter}');
    expect(within(popoverContainer).getByText('lb1')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    await waitFor(() => expect(within(popoverContainer).queryByText('Load Balancers')).not.toBeInTheDocument());
    expect(control).toHaveFocus();
  });

  it('restores trigger focus when Escape closes the popover from an action', async () => {
    const user = userEvent.setup();
    const { popoverContainer } = renderMultipleLoadBalancers();

    await user.tab();
    const control = await screen.findByRole('button', { name: '2 load balancers' });
    expect(await within(popoverContainer).findByText('Load Balancers')).toBeInTheDocument();
    await user.tab();
    expect(within(popoverContainer).getByRole('link', { name: /lb1/ })).toHaveFocus();

    await user.keyboard('{Escape}');

    await waitFor(() => expect(within(popoverContainer).queryByText('Load Balancers')).not.toBeInTheDocument());
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 110));
    });
    expect(within(popoverContainer).queryByText('Load Balancers')).not.toBeInTheDocument();
    expect(control).toHaveFocus();
  });

  it('keeps the popover open while focus moves through its actions and closes after blur', async () => {
    const user = userEvent.setup();
    const go = vi.spyOn(router.stateService, 'go').mockResolvedValue(undefined as any);
    const { popoverContainer } = renderMultipleLoadBalancers();

    await user.tab();
    await within(popoverContainer).findByText('Load Balancers');
    await user.tab();

    const firstLoadBalancer = within(popoverContainer).getByRole('link', { name: /lb1/ });
    expect(firstLoadBalancer).toHaveFocus();
    expect(within(popoverContainer).getByText('Load Balancers')).toBeInTheDocument();

    await user.keyboard('{Enter}');
    expect(go).toHaveBeenCalledWith('^.loadBalancerDetails', {
      accountId: 'prod',
      name: 'lb1',
      provider: 'aws',
      region: 'us-east-1',
    });

    await user.tab();
    expect(within(popoverContainer).getByRole('link', { name: /lb2/ })).toHaveFocus();
    await user.tab();
    await waitFor(() => expect(within(popoverContainer).queryByText('Load Balancers')).not.toBeInTheDocument());
  });
});
