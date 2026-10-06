import { fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';

import {
  EcsBuildInfoSection,
  EcsCapacitySection,
  EcsEnvironmentVariablesSection,
  EcsFirewallsSection,
  EcsHealthSection,
  EcsScalingPoliciesSection,
  EcsTaskDefinitionSection,
} from './EcsServerGroupDetailsSections';
import { AccountService } from '@spinnaker/core';

import { EcsServerGroupInformationSection } from './EcsServerGroupInformationSection';
import { EcsServerGroupEventsSection } from './EcsServerGroupEventsSection';

describe('ECS server group details sections', () => {
  const serverGroup = {
    account: 'test',
    region: 'us-east-1',
    createdTime: 1710000000000,
    ecsCluster: 'production',
    vpcId: 'vpc-123',
    taskDefinition: {
      taskName: 'fnord:42',
      containerImage: 'example/fnord:1.2.3',
      iamRole: 'fnord-task-role',
      containerPort: 8080,
      cpuUnits: 512,
      memoryReservation: 1024,
      memoryLimit: 2048,
      environmentVariables: [
        { name: 'ENVIRONMENT', value: 'production' },
        { name: 'REGION', value: 'eu' },
      ],
    },
    instanceCounts: { total: 2, up: 2, down: 0, unknown: 0, outOfService: 0, starting: 0 },
    securityGroups: ['sg-web', 'sg-admin'],
    instances: [{ id: 'one' }, { id: 'two' }],
    capacity: { desired: 3, min: 1, max: 5 },
    metricAlarms: ['cpu-high', 'memory-high'],
    buildInfo: {
      jenkins: { host: 'https://jenkins.example/', name: 'fnord', number: 123 },
      package_name: 'fnord-package',
      commit: '1234567890abcdef',
      version: '1.2.3',
    },
  } as any;
  const props = { app: {} as any, serverGroup };

  const expandSection = (heading: string): HTMLElement => {
    const headingElement = screen.getByRole('heading', { name: heading });
    const section = headingElement.closest('.collapsible-section') as HTMLElement;
    if (!section.querySelector('.content-body')) {
      fireEvent.click(headingElement);
    }
    return section;
  };

  beforeEach(() => vi.spyOn(AccountService, 'getAccountDetails').mockResolvedValue({} as any));

  it('renders general ECS location information', () => {
    render(<EcsServerGroupInformationSection {...props} />);
    const section = expandSection('Server Group Information');

    expect(within(section).getByText('production')).toBeInTheDocument();
    expect(within(section).getByText('vpc-123')).toBeInTheDocument();
  });

  it('renders task definition and container resources', () => {
    render(<EcsTaskDefinitionSection {...props} />);
    const section = expandSection('Task Definition');

    ['fnord:42', 'example/fnord:1.2.3', 'fnord-task-role', '8080', '512', '1024 MB', '2048 MB'].forEach((value) =>
      expect(within(section).getByText(value)).toBeInTheDocument(),
    );
  });

  it('renders environment variables and its empty state', () => {
    const rendered = render(<EcsEnvironmentVariablesSection {...props} />);
    let section = expandSection('Environment Variables');

    expect(within(section).getByText('ENVIRONMENT')).toBeInTheDocument();
    expect(within(section).getByText('production')).toBeInTheDocument();

    rendered.unmount();
    render(
      <EcsEnvironmentVariablesSection
        {...props}
        serverGroup={{ ...serverGroup, taskDefinition: { ...serverGroup.taskDefinition, environmentVariables: [] } }}
      />,
    );
    section = expandSection('Environment Variables');

    expect(within(section).getByText('This server group has no environment variables')).toBeInTheDocument();
  });

  it('renders health, firewalls, capacity, and scaling alarms', () => {
    const rendered = render(<EcsHealthSection {...props} />);
    expect(screen.getByRole('heading', { name: 'Health' })).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();

    rendered.rerender(<EcsFirewallsSection {...props} />);
    let section = expandSection('Firewalls');
    expect(within(section).getByText('sg-web')).toBeInTheDocument();
    expect(within(section).getByText('sg-admin')).toBeInTheDocument();

    rendered.rerender(<EcsCapacitySection {...props} />);
    section = expandSection('Capacity');
    expect(section).toHaveTextContent('Current2');
    expect(section).toHaveTextContent('Desired3');
    expect(section).toHaveTextContent('Min1');
    expect(section).toHaveTextContent('Max5');

    rendered.rerender(<EcsScalingPoliciesSection {...props} />);
    section = expandSection('Scaling Policies');
    expect(within(section).getByText('cpu-high')).toBeInTheDocument();
    expect(within(section).getByText('memory-high')).toBeInTheDocument();
  });

  it('renders the scaling alarms empty state', () => {
    render(<EcsScalingPoliciesSection {...props} serverGroup={{ ...serverGroup, metricAlarms: [] }} />);

    expect(
      within(expandSection('Scaling Policies')).getByText('There are no scaling policies assigned.'),
    ).toBeInTheDocument();
  });

  it('renders build metadata and a Jenkins link', () => {
    render(<EcsBuildInfoSection {...props} />);
    const section = expandSection('Build Data');

    expect(within(section).getByText('fnord-package')).toBeInTheDocument();
    expect(within(section).getByText('12345678')).toBeInTheDocument();
    expect(within(section).getByText('1.2.3')).toBeInTheDocument();
    expect(within(section).getByRole('link', { name: 'https://jenkins.example/job/fnord/123' })).toHaveAttribute(
      'href',
      'https://jenkins.example/job/fnord/123',
    );
  });

  it('renders the ECS events link', () => {
    render(<EcsServerGroupEventsSection {...props} />);

    expect(within(expandSection('ECS Events')).getByRole('link', { name: 'View Events' })).toBeInTheDocument();
  });
});
