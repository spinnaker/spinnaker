import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { EcsSecurityGroupDetailsComponent as EcsSecurityGroupDetails } from './EcsSecurityGroupDetails';

describe('EcsSecurityGroupDetails', () => {
  let router: UIRouterReact;
  const resolvedSecurityGroup = {
    accountId: 'test-account',
    name: 'web-sg',
    provider: 'ecs',
    region: 'eu-west-1',
    vpcId: 'vpc-1',
  };

  function app(isStandalone = false) {
    return {
      isStandalone,
      getDataSource: () => ({
        ready: () => Promise.resolve(),
        onRefresh: () => vi.fn(),
      }),
    } as any;
  }

  function renderDetails(component: React.ReactElement) {
    return render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: 'application.current', context: router.stateRegistry.get('application.current') as any }}
        >
          {component}
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );
  }

  beforeEach(() => {
    vi.spyOn(AccountService, 'getAccountDetails').mockResolvedValue({} as any);
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.current', 'application.firewallDetails'].forEach((name) =>
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` }),
    );
  });

  afterEach(() => router.dispose());

  it('replaces missing details through the injected state service', async () => {
    const stateService = { go: vi.fn() };
    renderDetails(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={{ getSecurityGroupDetails: () => Promise.resolve({}) } as any}
        stateService={stateService as any}
      />,
    );

    await waitFor(() =>
      expect(stateService.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' }),
    );
  });

  function securityGroup(name = 'web-sg') {
    return {
      accountId: 'test-account',
      accountName: 'test-account',
      description: 'Web ingress',
      id: `id-${name}`,
      ipRangeRules: [
        {
          protocol: 'tcp',
          range: { ip: '10.0.0.0', cidr: '/24' },
          portRanges: [{ startPort: 80, endPort: 80 }],
        },
      ],
      name,
      region: 'eu-west-1',
      securityGroupRules: [
        {
          protocol: 'tcp',
          portRanges: [{ startPort: 443, endPort: 443 }],
          securityGroup: {
            accountName: 'shared-account',
            id: 'sg-source',
            name: 'source-sg',
            region: 'eu-west-1',
            vpcId: 'vpc-1',
          },
        },
      ],
      vpcId: 'vpc-1',
    };
  }

  it('loads full details and renders descriptions, VPC names, and Amazon rule models with ECS links', async () => {
    const details = securityGroup();
    const securityGroupReader = {
      getApplicationSecurityGroup: vi.fn().mockReturnValue({}),
      getSecurityGroupDetails: vi.fn().mockReturnValue(Promise.resolve(details)),
    };
    const vpcReader = {
      getVpcName: vi.fn().mockReturnValue(Promise.resolve('Production VPC')),
    };
    renderDetails(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={vpcReader}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'web-sg' })).toBeInTheDocument();

    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      expect.anything(),
      'test-account',
      'ecs',
      'eu-west-1',
      'vpc-1',
      'web-sg',
    );
    expect(vpcReader.getVpcName).toHaveBeenCalledWith('vpc-1');
    expect(screen.getByText('Web ingress')).toBeInTheDocument();
    expect(screen.getByText('Production VPC')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('heading', { name: 'IP Range Rules (1)' }));
    expect(screen.getByText('10.0.0.0/24')).toBeInTheDocument();
    expect(screen.getByText(/tcp:80/)).toHaveTextContent('tcp:80 → 80');
    expect(screen.getByText('tcp: 443 → 443')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /source-sg \(sg-source\)/ })).toBeInTheDocument();
  });

  it('renders explicit empty states when no IP or referenced security-group rules exist', async () => {
    const details = { ...securityGroup(), ipRangeRules: [], securityGroupRules: [] };
    const securityGroupReader = {
      getApplicationSecurityGroup: () => ({}),
      getSecurityGroupDetails: () => Promise.resolve(details),
    };
    renderDetails(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={{ getVpcName: () => Promise.resolve(null) }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'web-sg' })).toBeInTheDocument();
    expect(screen.getAllByText('None')).toHaveLength(1);
    expect(screen.getByRole('heading', { name: 'IP Range Rules (0)' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('heading', { name: 'Firewall Rules (0)' }));
    expect(screen.getAllByText('None')).toHaveLength(2);
  });

  it('shows the standalone not-found state for empty and failed detail loads', async () => {
    const emptyReader = {
      getSecurityGroupDetails: () => Promise.resolve({}),
    };
    const failedReader = {
      getSecurityGroupDetails: () => Promise.reject(new Error('not found')),
    };
    const empty = renderDetails(
      <EcsSecurityGroupDetails
        app={app(true)}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={emptyReader as any}
      />,
    );
    expect(await screen.findByText(/Could not find.*web-sg/)).toBeInTheDocument();
    empty.unmount();

    renderDetails(
      <EcsSecurityGroupDetails
        app={app(true)}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={failedReader as any}
      />,
    );

    expect(await screen.findByText(/Could not find.*web-sg/)).toBeInTheDocument();
  });

  it('ignores stale detail and VPC responses after coordinates change', async () => {
    let resolveFirstDetails: (details: any) => void;
    let resolveFirstVpc: (name: string) => void;
    const firstDetails = new Promise<any>((resolve) => (resolveFirstDetails = resolve));
    const firstVpc = new Promise<string>((resolve) => (resolveFirstVpc = resolve));
    const securityGroupReader = {
      getApplicationSecurityGroup: () => ({}),
      getSecurityGroupDetails: vi
        .fn()
        .mockImplementation(
          (_app: any, _account: string, _provider: string, _region: string, _vpcId: string, name: string) =>
            name === 'web-sg' ? firstDetails : Promise.resolve({ ...securityGroup('api-sg'), vpcId: 'vpc-2' }),
        ),
    };
    const vpcReader = {
      getVpcName: vi
        .fn()
        .mockImplementation((vpcId: string) => (vpcId === 'vpc-1' ? firstVpc : Promise.resolve('API VPC'))),
    };
    const rendered = renderDetails(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={vpcReader}
      />,
    );

    await waitFor(() => expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledOnce());
    await act(async () => resolveFirstDetails(securityGroup()));
    rendered.rerender(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: 'application.current', context: router.stateRegistry.get('application.current') as any }}
        >
          <EcsSecurityGroupDetails
            app={app()}
            resolvedSecurityGroup={{ ...resolvedSecurityGroup, name: 'api-sg', vpcId: 'vpc-2' }}
            securityGroupReader={securityGroupReader as any}
            vpcReader={vpcReader}
          />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );
    expect(await screen.findByRole('heading', { name: 'api-sg' })).toBeInTheDocument();
    expect(screen.getByText('API VPC')).toBeInTheDocument();

    await act(async () => resolveFirstVpc('Stale VPC'));

    expect(screen.getByRole('heading', { name: 'api-sg' })).toBeInTheDocument();
    expect(screen.queryByText('Stale VPC')).not.toBeInTheDocument();
  });

  it('does not continue to VPC loading when a detail request resolves after unmount', async () => {
    let resolveDetails: (details: any) => void;
    const securityGroupReader = {
      getSecurityGroupDetails: vi.fn(() => new Promise<any>((resolve) => (resolveDetails = resolve))),
    };
    const vpcReader = { getVpcName: vi.fn().mockResolvedValue('Late VPC') };
    const rendered = renderDetails(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={vpcReader}
      />,
    );
    await waitFor(() => expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledOnce());
    rendered.unmount();
    await act(async () => resolveDetails(securityGroup()));

    expect(vpcReader.getVpcName).not.toHaveBeenCalled();
  });
});
