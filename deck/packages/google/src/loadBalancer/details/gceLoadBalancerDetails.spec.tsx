import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  HelpContentsRegistry,
  HelpTextExpandedContext,
  InfrastructureCaches,
  TaskExecutor,
} from '@spinnaker/core';

import { mockHttpClient } from '../../../../core/src/api/mock/mockHttpSupport';

import { GceLoadBalancerChoiceModal } from '../configure/choice/GceLoadBalancerChoiceModal';
import {
  GceLoadBalancerActions,
  GceLoadBalancerBackendServicesSection,
  GceLoadBalancerInformationSection,
  GceLoadBalancerListenersSection,
  loadGceLoadBalancerDetails,
} from './gceLoadBalancerDetails';

describe('GceLoadBalancerActions', () => {
  const app = { name: 'fnord' } as any;
  const loadBalancer = {
    account: 'account-a',
    instances: [],
    loadBalancerType: 'INTERNAL_MANAGED',
    name: 'fnord-main',
    region: 'europe-west1',
  } as any;
  const managedLoadBalancer = {
    ...loadBalancer,
    isManaged: true,
    managedResourceSummary: { id: 'resource-a', isPaused: false, locations: { account: 'account-a', regions: [] } },
  };

  it('hides write actions when the Google provider is disabled', () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(true);

    const { container } = render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('opens the current load balancer in edit mode through managed-resource gating', async () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);
    const show = vi.spyOn(GceLoadBalancerChoiceModal, 'show').mockReturnValue(Promise.resolve() as any);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);

    const managed = render(<GceLoadBalancerActions app={app} loadBalancer={managedLoadBalancer} />);
    fireEvent.click(screen.getByText('Edit Load Balancer'));
    await vi.waitFor(() =>
      expect(confirm).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ header: 'Pause Management?' })),
    );
    expect(show).not.toHaveBeenCalled();
    managed.unmount();

    render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    fireEvent.click(screen.getByText('Edit Load Balancer'));
    await vi.waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({
        app,
        application: app,
        forPipelineConfig: false,
        isNew: false,
        loadBalancer,
        mode: 'edit',
      } as any),
    );
  });

  it('keeps delete behind managed-resource gating and disables it while instances are attached', async () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);

    const editable = render(<GceLoadBalancerActions app={app} loadBalancer={managedLoadBalancer} />);
    fireEvent.click(editable.getByText('Delete Load Balancer'));
    await vi.waitFor(() =>
      expect(confirm).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ header: 'Pause Management?' })),
    );
    editable.unmount();
    confirm.mockClear();

    render(
      <GceLoadBalancerActions app={app} loadBalancer={{ ...loadBalancer, instances: [{ name: 'instance-a' }] }} />,
    );
    const attachedDelete = screen.getByText('Delete Load Balancer');
    expect(attachedDelete.closest('li')).toHaveClass('disabled');
    fireEvent.click(attachedDelete);
    await Promise.resolve();
    expect(confirm).not.toHaveBeenCalled();
  });
});

describe('loadGceLoadBalancerDetails', () => {
  it('matches scoped regional HTTP load balancers by raw urlMapName', async () => {
    const normalizedLoadBalancer = {
      account: 'test',
      defaultService: { healthCheck: { name: 'hc-default' }, name: 'backend-default' },
      hostRules: [],
      listeners: [{ name: 'regional-listener' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };
    const loadBalancerReader = {
      getLoadBalancerDetails: vi.fn().mockReturnValue(
        Promise.resolve([
          {
            dnsname: '1.2.3.4',
            listenerDescriptions: [{ listener: { loadBalancerPort: '443' } }],
          },
        ]),
      ),
    };
    const accountService = {
      getAccountDetails: vi.fn().mockReturnValue(Promise.resolve({ project: 'gce-project' })),
    };
    const autoClose = vi.fn();

    const loadBalancer = await loadGceLoadBalancerDetails({
      app: { loadBalancers: { data: [normalizedLoadBalancer] } } as any,
      autoClose,
      loadBalancerParams: {
        accountId: 'test',
        name: 'regional-url-map',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: loadBalancerReader as any,
      accountService: accountService as any,
    });

    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'gce',
      'test',
      'us-central1',
      'regional-listener',
    );
    expect(loadBalancer).toBe(normalizedLoadBalancer as any);
    expect((loadBalancer as any).logsLink).toContain('regional-url-map');
    expect((loadBalancer as any).logsLink).not.toContain('(test/us-central1/EXTERNAL_MANAGED)');
  });

  it('prefers an exact name over a regional URL-map alias', async () => {
    const autoClose = vi.fn();
    const loadBalancerReader = {
      getLoadBalancerDetails: vi.fn().mockReturnValue(Promise.resolve([])),
    };
    const accountService = {
      getAccountDetails: vi.fn().mockReturnValue(Promise.resolve({})),
    };
    const internalManaged = {
      account: 'test',
      listeners: [{ name: 'internal-listener' }],
      loadBalancerType: 'INTERNAL_MANAGED',
      name: 'shared-map',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'shared-map',
    };

    const loadBalancer = await loadGceLoadBalancerDetails({
      app: {
        loadBalancers: {
          data: [
            {
              account: 'test',
              listeners: [{ name: 'external-listener' }],
              loadBalancerType: 'EXTERNAL_MANAGED',
              name: 'shared-map (test/us-central1/EXTERNAL_MANAGED)',
              provider: 'gce',
              region: 'us-central1',
              urlMapName: 'shared-map',
            },
            internalManaged,
          ],
        },
      } as any,
      autoClose,
      loadBalancerParams: {
        accountId: 'test',
        name: 'shared-map',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: loadBalancerReader as any,
      accountService: accountService as any,
    });

    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancer).toBe(internalManaged as any);
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'gce',
      'test',
      'us-central1',
      'internal-listener',
    );
  });

  it('renders REGIONAL_EXTERNAL_NETWORK addresses without an HTTP URL scheme', async () => {
    const loadBalancer = {
      account: 'test',
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'passthrough-lb',
      provider: 'gce',
      region: 'us-central1',
    };
    await loadGceLoadBalancerDetails({
      accountService: { getAccountDetails: () => Promise.resolve({}) } as any,
      app: { loadBalancers: { data: [loadBalancer] } } as any,
      autoClose: vi.fn(),
      loadBalancerParams: {
        accountId: 'test',
        name: 'passthrough-lb',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: {
        getLoadBalancerDetails: () => Promise.resolve([{ dnsname: '203.0.113.10' }]),
      } as any,
    });

    // AccountTag lazily loads credentials; answer that request so the fail-closed test client allows it.
    const http = mockHttpClient();
    http.expectGET('/credentials').respond(200, []);
    const { container } = render(<GceLoadBalancerInformationSection app={{}} loadBalancer={loadBalancer} />);
    expect(screen.getByText('203.0.113.10')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('http://');
    expect(container).not.toHaveTextContent('https://');
    await http.flush();
  });

  it('reads back the REGIONAL_EXTERNAL_NETWORK backend service and health check', async () => {
    const loadBalancer = {
      account: 'test',
      backendService: { name: 'passthrough-backend', healthCheck: { name: 'tcp-hc' } },
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'passthrough-lb',
      provider: 'gce',
      region: 'us-central1',
    };
    await loadGceLoadBalancerDetails({
      accountService: { getAccountDetails: () => Promise.resolve({}) } as any,
      app: { loadBalancers: { data: [loadBalancer] } } as any,
      autoClose: vi.fn(),
      loadBalancerParams: {
        accountId: 'test',
        name: 'passthrough-lb',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: {
        getLoadBalancerDetails: () => Promise.resolve([{ dnsname: '203.0.113.10' }]),
      } as any,
    });

    render(<GceLoadBalancerBackendServicesSection app={{}} loadBalancer={loadBalancer} />);
    expect(screen.getByText('passthrough-backend')).toBeInTheDocument();
    expect(screen.getByText('tcp-hc')).toBeInTheDocument();
  });
});

describe('GceLoadBalancerActions delete behavior', () => {
  const app = { name: 'fnord' } as any;

  beforeEach(() => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);
  });

  it('deletes EXTERNAL_MANAGED load balancers using raw listener names and regional scope', async () => {
    const confirmSpy = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve({}) as any);
    vi.spyOn(InfrastructureCaches, 'clearCache').mockReturnValue(undefined);
    const executeTaskSpy = vi.spyOn(TaskExecutor, 'executeTask').mockReturnValue(Promise.resolve({}) as any);
    const loadBalancer = {
      account: 'test-account',
      instances: [],
      listeners: [{ name: 'regional-listener-443' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };

    render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    fireEvent.click(screen.getByText('Delete Load Balancer'));
    await vi.waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1));
    const modalParams = confirmSpy.mock.lastCall[0] as any;
    await modalParams.submitMethod({ deleteHealthChecks: true });

    expect(executeTaskSpy).toHaveBeenCalledWith({
      application: app,
      description: 'Delete load balancer: regional-url-map in test-account:us-central1',
      job: [
        expect.objectContaining({
          cloudProvider: 'gce',
          credentials: 'test-account',
          deleteHealthChecks: true,
          loadBalancerName: 'regional-listener-443',
          loadBalancerType: 'EXTERNAL_MANAGED',
          region: 'us-central1',
          regions: ['us-central1'],
          type: 'deleteLoadBalancer',
        }),
      ],
    });
  });

  it('shows delete-health-check controls for EXTERNAL_MANAGED and REGIONAL_EXTERNAL_NETWORK load balancers', async () => {
    const confirmSpy = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve({}) as any);
    const deleteBodyHasHealthCheckOption = async (loadBalancer: any): Promise<boolean> => {
      const callCount = confirmSpy.mock.calls.length;
      const actions = render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
      fireEvent.click(actions.getByText('Delete Load Balancer'));
      await vi.waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(callCount + 1));
      actions.unmount();

      const body = render(<>{(confirmSpy.mock.lastCall[0] as any).bodyContent}</>);
      const checkbox = body.queryByRole('checkbox', { name: 'Delete associated health checks' });
      body.unmount();
      return checkbox !== null;
    };

    const externalManaged = {
      account: 'test-account',
      instances: [],
      listeners: [{ name: 'regional-listener' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };
    expect(await deleteBodyHasHealthCheckOption(externalManaged)).toBe(true);

    const regionalExternalNetwork = {
      account: 'test-account',
      backendService: { healthCheck: { name: 'network-hc' } },
      instances: [],
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'regional-network-lb',
      provider: 'gce',
      region: 'us-central1',
    };
    expect(await deleteBodyHasHealthCheckOption(regionalExternalNetwork)).toBe(true);

    // INTERNAL deletes always remove unused health checks, so no option is offered.
    expect(await deleteBodyHasHealthCheckOption({ ...regionalExternalNetwork, loadBalancerType: 'INTERNAL' })).toBe(
      false,
    );
  });
});

describe('GceLoadBalancerListenersSection', () => {
  const app = { name: 'fnord' } as any;

  beforeEach(() => {
    vi.spyOn(HelpContentsRegistry, 'getHelpField').mockImplementation((id: string) => `help:${id}`);
  });

  // Expanded help renders the registered help contents inline, which makes the HelpField id observable.
  function renderListeners(loadBalancer: any) {
    return render(
      <HelpTextExpandedContext.Provider value={true}>
        <GceLoadBalancerListenersSection app={app} loadBalancer={loadBalancer} />
      </HelpTextExpandedContext.Provider>,
    );
  }

  function definitions(container: HTMLElement): string[] {
    return Array.from(container.querySelectorAll('dd')).map((node) => node.textContent);
  }

  it('keeps the historical listener list for existing load balancer families', () => {
    const network = renderListeners({
      loadBalancerType: 'NETWORK',
      listeners: [{ port: '8080' }],
      elb: {
        listenerDescriptions: [
          { listener: { instancePort: '8080', instanceProtocol: 'TCP', loadBalancerPort: '8080', protocol: 'TCP' } },
        ],
      },
    });

    expect(network.getAllByRole('listitem').map((node) => node.textContent)).toEqual(['8080']);
    expect(definitions(network.container)).toEqual([]);
    network.unmount();

    const http = renderListeners({
      loadBalancerType: 'HTTP',
      listeners: [{ port: '80' }, { port: '443', protocol: 'HTTPS' }],
      provider: 'gce',
    });

    expect(http.getAllByRole('listitem').map((node) => node.textContent)).toEqual(['80', 'HTTPS:443']);
    expect(http.queryByText(/^help:/)).not.toBeInTheDocument();
  });

  it('renders REGIONAL_EXTERNAL_NETWORK listenerDescriptions without named-port help', () => {
    const { container, queryByText } = renderListeners({
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      elb: {
        listenerDescriptions: [
          { listener: { instancePort: '8080', instanceProtocol: 'TCP', loadBalancerPort: '8080', protocol: 'TCP' } },
        ],
      },
    });

    expect(definitions(container)).toEqual(['TCP:8080 → TCP:8080']);
    expect(queryByText(/^help:/)).not.toBeInTheDocument();
  });

  it('prefers EXTERNAL_MANAGED elb.listenerDescriptions over normalized listeners', () => {
    const { container, getByText } = renderListeners({
      loadBalancerType: 'EXTERNAL_MANAGED',
      listeners: [{ port: '80' }, { port: '443' }],
      provider: 'gce',
      elb: {
        listenerDescriptions: [
          {
            listener: {
              instancePort: '8080',
              instanceProtocol: 'HTTP',
              loadBalancerPort: '443',
              protocol: 'HTTPS',
            },
          },
        ],
      },
    });

    expect(definitions(container)).toEqual(['HTTPS:443 → HTTP:8080']);
    expect(getByText('help:gce.httpLoadBalancer.namedPort')).toBeInTheDocument();
  });

  it('falls back to normalized EXTERNAL_MANAGED listeners only when elb.listenerDescriptions are absent', () => {
    const { container } = renderListeners({
      loadBalancerType: 'EXTERNAL_MANAGED',
      listeners: [
        { port: '80', name: 'frontend-80' },
        { port: '443', certificate: 'projects/p/certificates/cert', name: 'frontend-443' },
      ],
      provider: 'gce',
    });

    expect(definitions(container)).toEqual(['HTTP:80', 'HTTPS:443']);
  });

  it('shows no listeners configured when a regional external network load balancer has no descriptions', () => {
    const { container, getByText } = renderListeners({
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      listeners: [{ port: '8080' }],
    });

    expect(getByText('No listeners configured')).toBeInTheDocument();
    expect(definitions(container)).toEqual([]);
  });
});
