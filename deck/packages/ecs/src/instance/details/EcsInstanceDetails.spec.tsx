import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';
import type { Mock } from 'vitest';

import { VpcReader } from '@spinnaker/amazon';
import { AccountService, InstanceReader, RecentHistoryService, SubnetReader, timestamp } from '@spinnaker/core';

import { EcsInstanceDetailsComponent as EcsInstanceDetails } from './EcsInstanceDetails';

describe('EcsInstanceDetails', () => {
  let router: UIRouterReact;
  let stateService: { go: Mock };

  const withRouter = (component: React.ReactElement) => (
    <UIRouterContext.Provider value={router}>
      <UIViewContext.Provider
        value={{ fqn: 'application.instance', context: router.stateRegistry.get('application.instance') as any }}
      >
        {component}
      </UIViewContext.Provider>
    </UIRouterContext.Provider>
  );

  const renderDetails = (component: React.ReactElement) => render(withRouter(component));

  const expandSection = (heading: string): HTMLElement => {
    const headingElement = screen.getByRole('heading', { name: heading });
    const section = headingElement.closest('.collapsible-section') as HTMLElement;
    if (!section.querySelector('.content-body')) {
      fireEvent.click(headingElement);
    }
    return section;
  };

  const instanceDataLink = () => within(expandSection('Instance data')).getByRole('link', { name: 'Instance data' });

  const expectValue = (label: string, value: string) => {
    const section = screen
      .getByRole('heading', { name: 'Instance Information' })
      .closest('.collapsible-section') as HTMLElement;
    const term = within(section).getByText(label, { selector: 'dt' });
    expect(term.nextElementSibling).toHaveTextContent(value);
  };

  beforeEach(() => {
    stateService = { go: vi.fn() };
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.instance', 'application.serverGroup'].forEach((name) =>
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` }),
    );
    vi.spyOn(AccountService, 'getAccountDetails').mockResolvedValue({} as any);
    vi.spyOn(VpcReader, 'getVpcName').mockResolvedValue(null);
    vi.spyOn(SubnetReader, 'getSubnetPurpose').mockResolvedValue(null);
    vi.spyOn(RecentHistoryService, 'addExtraDataToLatest').mockReturnValue(undefined);
    vi.spyOn(RecentHistoryService, 'removeLastItem').mockReturnValue(undefined);
  });

  afterEach(() => router.dispose());

  it('loads routed details, merges the application summary, and renders all ECS detail sections', async () => {
    const app = withInstanceDataLink(application());
    const details = instanceDetails({ launchTime: 1700000000000 });
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(Promise.resolve(details) as any);

    const { container } = renderDetails(
      <EcsInstanceDetails
        app={app}
        environment="test"
        moniker={{ app: 'fnord', cluster: 'fnord-main' }}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'task-1' })).toBeInTheDocument();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'eu-west-1', 'task-1');
    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('instances', {
      account: 'test-account',
      region: 'eu-west-1',
      serverGroup: 'fnord-main-v001',
      vpcId: 'vpc-1',
    });
    expect(screen.queryByRole('heading', { name: 'Instance not found.' })).not.toBeInTheDocument();
    expect(container.querySelector('.InstanceDetailsHeader .close-button')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Instance Information' })).toBeInTheDocument();
    expectValue('In', 'test-account');
    expectValue('Server Group', 'fnord-main-v001');
    expectValue('Launched', timestamp(1700000000000));
    expectValue('Image ID', 'ami-complete');
    expect(screen.getByRole('heading', { name: 'Status' })).toBeInTheDocument();
    expect(screen.getByText('Ecs')).toBeInTheDocument();
    expect(screen.getByText('Up')).toBeInTheDocument();

    const networking = expandSection('Networking');
    ['10.0.0.10', '2001:db8::10', '172.17.0.2'].forEach((value) =>
      expect(within(networking).getByRole('link', { name: value })).toBeInTheDocument(),
    );
    expect(await within(networking).findByText('(vpc-1)')).toBeInTheDocument();
    expect(
      within(expandSection('Console Output')).getByRole('button', { name: 'Console Output (Raw)' }),
    ).toBeInTheDocument();
    expect(within(expandSection('Application')).getByRole('link', { name: 'Health endpoint' })).toHaveAttribute(
      'href',
      'http://task.example.test:8080/health',
    );
    expect(instanceDataLink()).toHaveAttribute('href', 'http://instance.test/ami-complete/fnord-lb/fnord-target');
  });

  it('loads complete details from a standalone instance prop', async () => {
    const app = withInstanceDataLink(application(true));
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(
      Promise.resolve(
        instanceDetails({ loadBalancers: ['standalone-lb'], targetGroups: ['standalone-target'] }),
      ) as any,
    );

    const { container } = renderDetails(
      <EcsInstanceDetails
        app={app}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'standalone-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'standalone-task' })).toBeInTheDocument();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith(
      'standalone-account',
      'us-east-1',
      'standalone-task',
    );
    expectValue('In', 'standalone-account');
    expect(container.querySelector('.InstanceDetailsHeader .close-button')).not.toBeInTheDocument();
    expect(instanceDataLink()).toHaveAttribute(
      'href',
      'http://instance.test/ami-complete/standalone-lb/standalone-target',
    );
  });

  it('finds string instance IDs in ECS target groups scoped to the routed account', async () => {
    const app = withInstanceDataLink(application(false, []));
    app.loadBalancers.data = [
      loadBalancer('wrong-account', 'wrong-target', ['target-task']),
      loadBalancer('test-account', 'correct-target', ['target-task']),
    ];
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(
      Promise.resolve(instanceDetails({ instanceId: 'target-task' })) as any,
    );

    renderDetails(
      <EcsInstanceDetails
        app={app}
        accountId="test-account"
        $stateParams={{ provider: 'ecs', instanceId: 'target-task' }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'target-task' })).toBeInTheDocument();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'eu-west-1', 'target-task');
    expect(instanceDataLink()).toHaveAttribute('href', 'http://instance.test/ami-complete//correct-target');
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
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(
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

    renderDetails(<EcsInstanceDetails app={app} $stateParams={{ provider: 'ecs', instanceId: 'task-1' }} />);

    const healthCheck = await screen.findByRole('link', { name: 'Health Check' });
    expect(healthCheck).toHaveAttribute('href', 'https://10.0.0.10:8443/health');
    expect(screen.getAllByRole('link', { name: 'Health Check' })).toHaveLength(1);
  });

  it('renders the ECS zone before availability zone fallbacks', async () => {
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(
      Promise.resolve(instanceDetails({ availabilityZone: 'fallback-zone', zone: 'ecs-zone' })) as any,
    );
    renderDetails(<EcsInstanceDetails app={application()} $stateParams={{ provider: 'ecs', instanceId: 'task-1' }} />);

    expect(await screen.findByRole('heading', { name: 'task-1' })).toBeInTheDocument();
    expectValue('In', 'ecs-zone');
    expect(screen.queryByText(/fallback-zone/)).not.toBeInTheDocument();
  });

  it('shows an inline not-found state when a standalone load fails', async () => {
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(Promise.reject(new Error('not found')) as any);
    renderDetails(
      <EcsInstanceDetails
        app={application(true)}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'missing-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Instance not found.' })).toBeInTheDocument();
    expect(screen.getByText('missing-task', { selector: 'p' })).toBeInTheDocument();
    expect(RecentHistoryService.removeLastItem).toHaveBeenCalledWith('instances');
    expect(stateService.go).not.toHaveBeenCalled();
  });

  it('closes routed details when loading fails', async () => {
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(Promise.reject(new Error('not found')) as any);
    renderDetails(
      <EcsInstanceDetails
        app={application()}
        environment="test"
        moniker={{ app: 'fnord' }}
        router={{} as any}
        stateParams={{}}
        stateService={stateService as any}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    await waitFor(() =>
      expect(stateService.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' }),
    );
  });

  it('keeps newer instance details when an older request resolves last', async () => {
    const oldRequest = deferred<any>();
    const newRequest = deferred<any>();
    const app = application(false, [
      serverGroup('fnord-main-v001', 'task-1'),
      serverGroup('fnord-main-v002', 'task-2'),
    ]);
    vi.spyOn(
      InstanceReader,
      'getInstanceDetails',
    ).mockImplementation((_account: string, _region: string, instanceId: string) =>
      instanceId === 'task-1' ? oldRequest.promise : newRequest.promise,
    );
    const rendered = renderDetails(
      <EcsInstanceDetails
        app={app}
        environment="test"
        moniker={{ app: 'fnord' }}
        $stateParams={{ provider: 'ecs', instanceId: 'task-1' }}
      />,
    );

    await waitFor(() => expect(InstanceReader.getInstanceDetails).toHaveBeenCalledOnce());
    rendered.rerender(
      withRouter(
        <EcsInstanceDetails
          app={app}
          environment="test"
          moniker={{ app: 'fnord' }}
          $stateParams={{ provider: 'ecs', instanceId: 'task-2' }}
        />,
      ),
    );
    await act(async () => newRequest.resolve(instanceDetails({ instanceId: 'task-2', name: 'task-2' })));
    expect(await screen.findByRole('heading', { name: 'task-2' })).toBeInTheDocument();

    await act(async () => oldRequest.resolve(instanceDetails({ instanceId: 'task-1', name: 'task-1' })));

    expect(screen.getByRole('heading', { name: 'task-2' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'task-1' })).not.toBeInTheDocument();
  });

  it('does not remove standalone history when a request rejects after unmount', async () => {
    const request = deferred<any>();
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(request.promise as any);
    const rendered = renderDetails(
      <EcsInstanceDetails
        app={application(true)}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'standalone-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );
    await waitFor(() => expect(InstanceReader.getInstanceDetails).toHaveBeenCalledOnce());
    vi.mocked(RecentHistoryService.removeLastItem).mockClear();
    rendered.unmount();
    await act(async () => request.reject(new Error('late failure')));

    expect(RecentHistoryService.removeLastItem).not.toHaveBeenCalled();
  });

  it('does not update state when a request resolves after unmount', async () => {
    const request = deferred<any>();
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(request.promise as any);
    const consoleError = vi.spyOn(console, 'error');
    const rendered = renderDetails(
      <EcsInstanceDetails
        app={application(true)}
        environment="test"
        instance={{ account: 'standalone-account', instanceId: 'standalone-task', region: 'us-east-1' }}
        moniker={{ app: 'fnord' }}
      />,
    );
    await waitFor(() => expect(InstanceReader.getInstanceDetails).toHaveBeenCalledOnce());
    rendered.unmount();
    await act(async () => request.resolve(instanceDetails()));

    expect(consoleError.mock.calls.flat().join(' ')).not.toContain('unmounted component');
  });
});

function withInstanceDataLink(app: any): any {
  app.attributes.instanceLinks.push({
    title: 'Instance data',
    links: [{ title: 'Instance data', path: 'http://instance.test/{{imageId}}/{{loadBalancers}}/{{targetGroups}}' }],
  });
  return app;
}

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
          onRefresh: () => vi.fn(),
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

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}
