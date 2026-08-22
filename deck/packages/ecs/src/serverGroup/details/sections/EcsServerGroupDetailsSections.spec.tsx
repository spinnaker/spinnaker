import React from 'react';
import { shallow } from 'enzyme';

import { CollapsibleSection, HealthCounts } from '@spinnaker/core';

import {
  EcsBuildInfoSection,
  EcsCapacitySection,
  EcsEnvironmentVariablesSection,
  EcsFirewallsSection,
  EcsHealthSection,
  EcsScalingPoliciesSection,
  EcsTaskDefinitionSection,
} from './EcsServerGroupDetailsSections';
import { EcsServerGroupInformationSection } from './EcsServerGroupInformationSection';
import { EventsLink } from '../../events/EventsLink';
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

  const sectionContent = (wrapper: any) => shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);

  it('renders general ECS location information', () => {
    const wrapper = shallow(<EcsServerGroupInformationSection {...props} />);

    expect(sectionContent(wrapper).text()).toContain('production');
    expect(sectionContent(wrapper).text()).toContain('vpc-123');
  });

  it('renders task definition and container resources', () => {
    const wrapper = shallow(<EcsTaskDefinitionSection {...props} />);
    const text = sectionContent(wrapper).text();

    expect(text).toContain('fnord:42');
    expect(text).toContain('example/fnord:1.2.3');
    expect(text).toContain('fnord-task-role');
    expect(text).toContain('8080');
    expect(text).toContain('512');
    expect(text).toContain('1024 MB');
    expect(text).toContain('2048 MB');
  });

  it('renders environment variables and its empty state', () => {
    const populated = shallow(<EcsEnvironmentVariablesSection {...props} />);
    const empty = shallow(
      <EcsEnvironmentVariablesSection
        {...props}
        serverGroup={{ ...serverGroup, taskDefinition: { ...serverGroup.taskDefinition, environmentVariables: [] } }}
      />,
    );

    expect(sectionContent(populated).text()).toContain('ENVIRONMENT');
    expect(sectionContent(populated).text()).toContain('production');
    expect(sectionContent(empty).text()).toContain('This server group has no environment variables');
  });

  it('renders health, firewalls, capacity, and scaling alarms', () => {
    const health = shallow(<EcsHealthSection {...props} />);
    const firewalls = shallow(<EcsFirewallsSection {...props} />);
    const capacity = shallow(<EcsCapacitySection {...props} />);
    const alarms = shallow(<EcsScalingPoliciesSection {...props} />);

    expect(health.find(HealthCounts).prop('container')).toBe(serverGroup.instanceCounts);
    expect(sectionContent(firewalls).text()).toContain('sg-web');
    expect(sectionContent(firewalls).text()).toContain('sg-admin');
    expect(sectionContent(capacity).text()).toContain('Current2');
    expect(sectionContent(capacity).text()).toContain('Desired3');
    expect(sectionContent(capacity).text()).toContain('Min1');
    expect(sectionContent(capacity).text()).toContain('Max5');
    expect(sectionContent(alarms).text()).toContain('cpu-high');
    expect(sectionContent(alarms).text()).toContain('memory-high');
  });

  it('renders the scaling alarms empty state', () => {
    const wrapper = shallow(
      <EcsScalingPoliciesSection {...props} serverGroup={{ ...serverGroup, metricAlarms: [] }} />,
    );

    expect(sectionContent(wrapper).text()).toContain('There are no scaling policies assigned.');
  });

  it('renders build metadata and a Jenkins link', () => {
    const wrapper = shallow(<EcsBuildInfoSection {...props} />);
    const content = sectionContent(wrapper);
    const text = content.text();

    expect(text).toContain('fnord-package');
    expect(text).toContain('12345678');
    expect(text).toContain('1.2.3');
    expect(content.find('a').prop('href')).toBe('https://jenkins.example/job/fnord/123');
  });

  it('renders the ECS events link', () => {
    const wrapper = shallow(<EcsServerGroupEventsSection {...props} />);

    expect(wrapper.find(EventsLink).prop('serverGroup')).toBe(serverGroup);
  });
});
