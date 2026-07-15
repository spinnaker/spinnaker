import { shallow } from 'enzyme';
import React from 'react';

import { InstanceInformation, InstanceStatus, VpcTag } from '@spinnaker/amazon';
import {
  AngularServices,
  CollapsibleSection,
  ConsoleOutputLink,
  InstanceDetailsHeader,
  InstanceLinks,
  InstanceReader,
  LabeledValue,
  RecentHistoryService,
} from '@spinnaker/core';

import { EcsInstanceDetails } from './EcsInstanceDetails';

describe('EcsInstanceDetails', () => {
  let $state: { go: jasmine.Spy };

  beforeEach(() => {
    $state = { go: jasmine.createSpy('go') };
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue($state as any);
    spyOn(RecentHistoryService, 'addExtraDataToLatest');
    spyOn(RecentHistoryService, 'removeLastItem');
  });

  it('loads routed details, merges the application summary, and renders all ECS detail sections', async () => {
    const app = application();
    const details = instanceDetails();
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(Promise.resolve(details) as any);

    const wrapper = shallow(
      <EcsInstanceDetails
        app={app}
        environment="test"
        moniker={{ app: 'fnord', cluster: 'fnord-main' }}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    await settle();
    wrapper.update();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'eu-west-1', 'task-1');
    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('instances', {
      account: 'test-account',
      region: 'eu-west-1',
      serverGroup: 'fnord-main-v001',
      vpcId: 'vpc-1',
    });
    expect(wrapper.find(InstanceDetailsHeader).props()).toEqual(
      jasmine.objectContaining({ healthState: 'Up', instanceId: 'task-1', loading: false, standalone: false }),
    );

    const information = wrapper.find(InstanceInformation);
    expect(information.props()).toEqual(
      jasmine.objectContaining({
        account: 'test-account',
        launchTime: 200,
        provider: 'ecs',
        region: 'eu-west-1',
        serverGroup: 'fnord-main-v001',
      }),
    );
    expect(
      wrapper
        .find(LabeledValue)
        .filterWhere((value) => value.prop('label') === 'Image ID')
        .prop('value'),
    ).toBe('ami-complete');
    expect(wrapper.find(InstanceStatus).props()).toEqual(
      jasmine.objectContaining({
        healthMetrics: [jasmine.objectContaining({ description: 'healthy in ECS', state: 'Up', type: 'Ecs' })],
        healthState: 'Up',
      }),
    );
    const networking = wrapper
      .find(CollapsibleSection)
      .filterWhere((section) => section.prop('heading') === 'Networking');
    const vpc = shallow(<div>{networking.prop('children')}</div>)
      .find(LabeledValue)
      .filterWhere((value) => value.prop('label') === 'VPC');
    expect(vpc.prop('value')).toEqual(<VpcTag vpcId="vpc-1" />);
    const networkAddresses = shallow(<div>{networking.prop('children')}</div>)
      .find('NetworkAddress')
      .map((address) => address.prop('address'));
    expect(networkAddresses).toEqual(['10.0.0.10', '2001:db8::10', '172.17.0.2']);
    expect(wrapper.find(ConsoleOutputLink).prop('instance')).toEqual(
      jasmine.objectContaining({ imageId: 'ami-complete' }),
    );

    const links = wrapper.find(InstanceLinks);
    expect(links.props()).toEqual(
      jasmine.objectContaining({ address: 'task.example.test', application: app, environment: 'test' }),
    );
    const renderedLinks = shallow(<InstanceLinks {...(links.props() as any)} />);
    const linkSection = shallow(<div>{renderedLinks.find(CollapsibleSection).prop('children')}</div>);
    expect(linkSection.text()).toContain('Health endpoint');
    expect(linkSection.find('a').prop('href')).toBe('http://task.example.test:8080/health');
  });

  it('loads complete details from a standalone instance prop', async () => {
    const app = application(true);
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(
      Promise.resolve(
        instanceDetails({ loadBalancers: ['standalone-lb'], targetGroups: ['standalone-target'] }),
      ) as any,
    );

    const wrapper = shallow(
      <EcsInstanceDetails
        app={app}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'standalone-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );

    await settle();
    wrapper.update();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith(
      'standalone-account',
      'us-east-1',
      'standalone-task',
    );
    expect(wrapper.find(InstanceDetailsHeader).props()).toEqual(
      jasmine.objectContaining({ instanceId: 'standalone-task', standalone: true }),
    );
    expect(wrapper.find(ConsoleOutputLink).prop('instance')).toEqual(
      jasmine.objectContaining({ loadBalancers: ['standalone-lb'], targetGroups: ['standalone-target'] }),
    );
  });

  it('finds string instance IDs in ECS target groups scoped to the routed account', async () => {
    const app = application(false, []);
    app.loadBalancers.data = [
      loadBalancer('wrong-account', 'wrong-target', ['target-task']),
      loadBalancer('test-account', 'correct-target', ['target-task']),
    ];
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(
      Promise.resolve(instanceDetails({ instanceId: 'target-task' })) as any,
    );

    const wrapper = shallow(
      <EcsInstanceDetails
        app={app}
        accountId="test-account"
        $stateParams={{ provider: 'ecs', instanceId: 'target-task' }}
      />,
    );

    await settle();
    wrapper.update();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'eu-west-1', 'target-task');
    expect(wrapper.find(ConsoleOutputLink).prop('instance')).toEqual(
      jasmine.objectContaining({ targetGroups: ['correct-target'] }),
    );
  });

  it('adds health check details only from the matching account and region target group', async () => {
    const app = application();
    app.serverGroups.data[0].instances[0].health = [
      {
        state: 'Up',
        targetGroups: [
          { state: 'healthy', targetGroupName: 'fnord-target' },
          { state: 'healthy', targetGroupName: 'missing-target' },
        ],
        type: 'TargetGroup',
      },
    ];
    app.loadBalancers.data = [
      loadBalancer('test-account', 'fnord-target', [], 'us-east-1', {
        healthCheckPath: '/wrong-region',
        healthCheckPort: 80,
        healthCheckProtocol: 'HTTP',
      }),
      loadBalancer('test-account', 'unrelated-target', [], 'eu-west-1', {
        healthCheckPath: '/wrong-target',
        healthCheckPort: 81,
        healthCheckProtocol: 'HTTP',
      }),
      loadBalancer('test-account', 'fnord-target', [], 'eu-west-1', {
        healthCheckPath: '/health',
        healthCheckPort: 8443,
        healthCheckProtocol: 'HTTPS',
      }),
    ];
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(
      Promise.resolve(
        instanceDetails({
          health: [
            {
              state: 'Up',
              targetGroups: [
                { state: 'healthy', targetGroupName: 'fnord-target' },
                { state: 'healthy', targetGroupName: 'missing-target' },
              ],
              type: 'TargetGroup',
            },
          ],
        }),
      ) as any,
    );

    const wrapper = shallow(<EcsInstanceDetails app={app} $stateParams={{ provider: 'ecs', instanceId: 'task-1' }} />);

    await settle();
    wrapper.update();

    const [matching, missing] = (wrapper.find(InstanceStatus).prop('healthMetrics') as any[])[0].targetGroups;
    expect(matching).toEqual(
      jasmine.objectContaining({ healthCheckPath: ':8443/health', healthCheckProtocol: 'https' }),
    );
    expect(missing.healthCheckPath).toBeUndefined();
    expect(missing.healthCheckProtocol).toBeUndefined();
  });

  it('renders the ECS zone before availability zone fallbacks', async () => {
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(
      Promise.resolve(instanceDetails({ availabilityZone: 'fallback-zone', zone: 'ecs-zone' })) as any,
    );
    const wrapper = shallow(
      <EcsInstanceDetails app={application()} $stateParams={{ provider: 'ecs', instanceId: 'task-1' }} />,
    );

    await settle();
    wrapper.update();

    expect(wrapper.find(InstanceInformation).prop('availabilityZone')).toBe('ecs-zone');
  });

  it('shows an inline not-found state when a standalone load fails', async () => {
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(Promise.reject(new Error('not found')) as any);
    const wrapper = shallow(
      <EcsInstanceDetails
        app={application(true)}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'missing-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );

    await settle();
    wrapper.update();

    expect(wrapper.text()).toContain('Instance not found.');
    expect(wrapper.text()).toContain('missing-task');
    expect(RecentHistoryService.removeLastItem).toHaveBeenCalledWith('instances');
    expect($state.go).not.toHaveBeenCalled();
  });

  it('closes routed details when loading fails', async () => {
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(Promise.reject(new Error('not found')) as any);
    shallow(
      <EcsInstanceDetails
        app={application()}
        environment="test"
        moniker={{ app: 'fnord' }}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    await settle();

    expect($state.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' });
  });

  it('keeps newer instance details when an older request resolves last', async () => {
    const oldRequest = deferred<any>();
    const newRequest = deferred<any>();
    const app = application(false, [
      serverGroup('fnord-main-v001', 'task-1'),
      serverGroup('fnord-main-v002', 'task-2'),
    ]);
    spyOn(InstanceReader, 'getInstanceDetails').and.callFake((_account: string, _region: string, instanceId: string) =>
      instanceId === 'task-1' ? oldRequest.promise : newRequest.promise,
    );
    const wrapper = shallow(
      <EcsInstanceDetails
        app={app}
        environment="test"
        moniker={{ app: 'fnord' }}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    await settle();
    wrapper.setProps({ $stateParams: { provider: 'ecs', instanceId: 'task-2' } });
    newRequest.resolve(instanceDetails({ instanceId: 'task-2', name: 'task-2' }));
    await settle();
    wrapper.update();
    expect(wrapper.find(InstanceDetailsHeader).prop('instanceId')).toBe('task-2');

    oldRequest.resolve(instanceDetails({ instanceId: 'task-1', name: 'task-1' }));
    await settle();
    wrapper.update();

    expect(wrapper.find(InstanceDetailsHeader).prop('instanceId')).toBe('task-2');
  });

  it('does not update state when a request resolves after unmount', async () => {
    const request = deferred<any>();
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(request.promise as any);
    const wrapper = shallow(
      <EcsInstanceDetails
        app={application(true)}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'standalone-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );
    const component = wrapper.instance() as React.Component;
    spyOn(component, 'setState');

    wrapper.unmount();
    request.resolve(instanceDetails());
    await settle();

    expect(component.setState).not.toHaveBeenCalled();
  });
});

function application(isStandalone = false, serverGroups = [serverGroup()]): any {
  return {
    attributes: {
      instanceLinks: [{ title: 'Application', links: [{ title: 'Health endpoint', path: '/health' }] }],
      instancePort: 8080,
    },
    isStandalone,
    loadBalancers: {
      data: [],
      ready: () => Promise.resolve(),
    },
    serverGroups: isStandalone
      ? undefined
      : {
          data: serverGroups,
          onRefresh: () => jasmine.createSpy('unsubscribe'),
          ready: () => Promise.resolve(),
        },
  };
}

function serverGroup(name = 'fnord-main-v001', instanceId = 'task-1'): any {
  return {
    account: 'test-account',
    instances: [
      {
        health: [{ state: 'Up', type: 'Ecs' }],
        healthState: 'Up',
        id: instanceId,
        launchTime: 100,
        name: instanceId,
      },
    ],
    loadBalancers: ['fnord-lb'],
    name,
    region: 'eu-west-1',
    targetGroup: ['fnord-target'],
    vpcId: 'vpc-1',
  };
}

function instanceDetails(overrides: any = {}): any {
  return {
    health: [{ description: 'healthy in ECS', state: 'Up', type: 'Ecs' }],
    imageId: 'ami-complete',
    instanceId: 'task-1',
    launchTime: 200,
    name: 'task-1',
    networkInterface: {
      ipv6Address: '2001:db8::10',
      privateIpv4Address: '10.0.0.10',
    },
    privateAddress: '172.17.0.2',
    publicDnsName: 'task.example.test',
    subnetId: 'subnet-1',
    ...overrides,
  };
}

function loadBalancer(
  account: string,
  targetGroupName: string,
  instances: Array<string | { id: string }>,
  region = 'eu-west-1',
  targetGroupOverrides: any = {},
): any {
  return {
    account,
    instances: [],
    name: `${targetGroupName}-load-balancer`,
    region,
    targetGroups: [
      {
        account,
        healthCheckPath: '/default',
        healthCheckPort: 'traffic-port',
        healthCheckProtocol: 'HTTP',
        instances,
        port: 8080,
        region,
        targetGroupName,
        ...targetGroupOverrides,
      },
    ],
    vpcId: 'vpc-1',
  };
}

const settle = () => new Promise((resolve) => setTimeout(resolve));

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}
