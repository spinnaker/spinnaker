import { render, screen } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';
import { mockInstance } from '@spinnaker/mocks';

import { InstanceInformation } from './InstanceInformation';

describe('InstanceInformation', () => {
  let router: UIRouterReact;
  const testInstance = {
    ...mockInstance,
    instanceType: 'm5.large',
    capacityType: 'spot',
    region: 'us-east-1',
    serverGroup: 'test_sg',
  };

  const expectValue = (label: string, value: string) => {
    const term = screen.getByText(label, { selector: 'dt' });
    expect(term.nextElementSibling).toHaveTextContent(value);
  };

  const renderInformation = (component: React.ReactElement) =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: 'application.instance', context: router.stateRegistry.get('application.instance') as any }}
        >
          {component}
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  beforeEach(() => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.instance', 'application.serverGroup'].forEach((name) =>
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` }),
    );
  });

  afterEach(() => router.dispose());

  it('should render correct state when all attributes exist', () => {
    renderInformation(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={testInstance.availabilityZone}
        instanceType={testInstance.instanceType}
        capacityType={testInstance.capacityType}
        launchTime={testInstance.launchTime}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={testInstance.serverGroup}
        showInstanceType={true}
      />,
    );

    expectValue('Launched', '1970-01-14 22:37:37 PST');
    expectValue('In', testInstance.availabilityZone);
    expectValue('Type', testInstance.instanceType);
    expectValue('Capacity Type', testInstance.capacityType);
    expect(screen.getByRole('link', { name: testInstance.serverGroup })).toBeInTheDocument();
  });

  it('should render correct state when attributes are missing', () => {
    renderInformation(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={undefined}
        instanceType={undefined}
        capacityType={undefined}
        launchTime={undefined}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={undefined}
        showInstanceType={true}
      />,
    );

    expectValue('Launched', 'Unknown');
    expectValue('In', 'Unknown');
    expectValue('Type', 'Unknown');
    expect(screen.queryByText('Capacity Type', { selector: 'dt' })).not.toBeInTheDocument();
    expect(screen.queryByText('Server Group', { selector: 'dt' })).not.toBeInTheDocument();
  });
});
