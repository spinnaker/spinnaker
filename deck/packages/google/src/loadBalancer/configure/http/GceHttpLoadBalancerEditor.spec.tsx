import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import {
  buildGceHttpLoadBalancerOptions,
  constrainGceHttpLoadBalancerCommand,
  GceHttpLoadBalancerEditor,
  validateGceHttpLoadBalancerCommand,
} from './GceHttpLoadBalancerEditor';
import type { IGceLoadBalancerData } from '../common';
import { normalizeGceLoadBalancerCommand } from '../common';

describe('GceHttpLoadBalancerEditor', () => {
  const emptyData: IGceLoadBalancerData = {
    accounts: [],
    addresses: [],
    backendServices: [],
    certificates: [],
    healthChecks: [],
    networks: [],
    regions: [],
    subnets: [],
  };

  it('constrains HTTP and INTERNAL_MANAGED location and listener protocols', () => {
    const http = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        listeners: [{ certificate: 'cert-a', name: 'frontend', port: 443, protocol: 'HTTPS', subnet: 'subnet-a' }],
        loadBalancerType: 'HTTP',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'create',
    );

    expect(constrainGceHttpLoadBalancerCommand(http)).toEqual(
      expect.objectContaining({
        loadBalancerType: 'HTTP',
        network: undefined,
        region: 'global',
        subnet: undefined,
      }),
    );
    expect(constrainGceHttpLoadBalancerCommand(http).listeners).toEqual([
      {
        certificate: { name: 'cert-a' },
        name: 'frontend',
        portRange: '443',
        protocol: 'HTTPS',
      },
    ]);

    const internal = constrainGceHttpLoadBalancerCommand({
      ...http,
      loadBalancerType: 'INTERNAL_MANAGED',
      network: { name: 'network-a' },
      region: 'europe-west1',
      subnet: { name: 'subnet-a' },
    });

    expect(internal.region).toBe('europe-west1');
    expect(internal.listeners).toEqual([
      {
        certificate: { name: 'cert-a' },
        name: 'frontend',
        portRange: '443',
        protocol: 'HTTPS',
        subnet: { name: 'subnet-a' },
      },
    ]);
  });

  it('removes certificates from plaintext HTTP listeners', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        listeners: [{ certificate: 'stale-cert', name: 'plaintext', port: 80, protocol: 'HTTP' }],
        loadBalancerType: 'HTTP',
        name: 'web',
      },
      'create',
    );

    expect(constrainGceHttpLoadBalancerCommand(command).listeners).toEqual([
      { name: 'plaintext', portRange: '80', protocol: 'HTTP' },
    ]);
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`constrains ${loadBalancerType} HTTPS listeners to port 443`, () => {
      const command = normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          certificate: 'cert-a',
          listeners: [{ certificate: 'cert-a', name: 'frontend', port: 8443, protocol: 'HTTPS' }],
          loadBalancerType,
          name: 'web',
          network: loadBalancerType === 'INTERNAL_MANAGED' ? 'network-a' : undefined,
          region: loadBalancerType === 'INTERNAL_MANAGED' ? 'europe-west1' : 'global',
          subnet: loadBalancerType === 'INTERNAL_MANAGED' ? 'subnet-a' : undefined,
        },
        'create',
      );

      expect(constrainGceHttpLoadBalancerCommand(command).listeners[0].portRange).toBe('443');
    });
  });

  it('renders only protocols supported by the selected composite type', () => {
    const onChange = vi.fn();
    const httpCommand = normalizeGceLoadBalancerCommand(
      { account: 'account-a', listeners: [{ name: 'frontend', port: 80 }], loadBalancerType: 'HTTP', name: 'web' },
      'create',
    );
    const http = render(<GceHttpLoadBalancerEditor command={httpCommand} data={emptyData} onChange={onChange} />);

    expect(optionValues(http.getByTestId('listener-protocol'))).toEqual(['HTTP', 'HTTPS']);
    http.unmount();

    const internal = render(
      <GceHttpLoadBalancerEditor
        command={{ ...httpCommand, loadBalancerType: 'INTERNAL_MANAGED', region: 'europe-west1' }}
        data={emptyData}
        onChange={onChange}
      />,
    );

    expect(optionValues(internal.getByTestId('listener-protocol'))).toEqual(['HTTP', 'HTTPS']);
  });

  it('renders location controls as form rows instead of nesting them inside bold labels', () => {
    const command = normalizeGceLoadBalancerCommand(
      { account: 'account-a', listeners: [{ name: 'frontend', port: 80 }], loadBalancerType: 'HTTP', name: 'web' },
      'create',
    );
    const { container } = render(<GceHttpLoadBalancerEditor command={command} data={emptyData} onChange={vi.fn()} />);
    expect(container.querySelectorAll('.form-group').length).toBeGreaterThan(2);
  });

  it('validates account, name, location, listeners, backends, health checks, and routing', () => {
    const invalid = normalizeGceLoadBalancerCommand(
      {
        account: '',
        hostRules: [
          {
            hostPatterns: [],
            pathMatcher: { pathRules: [{ paths: [] }] },
          },
        ],
        listeners: [
          { name: 'invalid-protocol', port: '80-81', protocol: 'SSL' },
          { name: 'missing-certificate', port: 443, protocol: 'HTTPS' },
          { certificate: 'cert-a', name: 'invalid-https-port', port: 8443, protocol: 'HTTPS' },
        ],
        loadBalancerType: 'INTERNAL_MANAGED',
        name: ' ',
        region: '',
      },
      'create',
    );
    invalid.backendServices = [{ name: '' }];
    invalid.healthChecks = [
      { healthCheckType: 'HTTP', name: '', port: 70000, requestPath: '' },
      { healthCheckType: 'UDP', name: 'unsupported-check', port: 80 },
    ];
    expect(validateGceHttpLoadBalancerCommand(invalid)).toEqual(
      expect.arrayContaining([
        'Name is required.',
        'Account is required.',
        'Region is required for INTERNAL_MANAGED load balancers.',
        'Network is required for INTERNAL_MANAGED load balancers.',
        'Subnet is required for INTERNAL_MANAGED load balancers.',
        'Listener protocol must be HTTP or HTTPS.',
        'Listener port must be a single port between 1 and 65535.',
        'Certificate is required for HTTPS listeners.',
        'HTTPS listeners must use port 443.',
        'Backend service name is required.',
        'Each backend service requires a health check.',
        'Health check name is required.',
        'Health check protocol must be HTTP, HTTPS, TCP, or SSL.',
        'Health check port must be between 1 and 65535.',
        'HTTP and HTTPS health checks require a request path.',
        'Default backend service is required.',
        'Host rules require at least one host pattern.',
        'Path matcher default backend service is required.',
        'Path rules require at least one path.',
        'Path rules require a backend service.',
      ]),
    );
  });

  it('initializes path matchers from the current composite default without coupling later changes', () => {
    const onChange = vi.fn();
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ name: 'backend-a' }, { name: 'backend-b' }],
        defaultService: 'backend-a',
        listeners: [{ name: 'frontend', port: 80, protocol: 'HTTP' }],
        loadBalancerType: 'HTTP',
        name: 'web',
      },
      'create',
    );
    const data = { ...emptyData, backendServices: command.backendServices };
    const initial = render(<GceHttpLoadBalancerEditor command={command} data={data} onChange={onChange} />);

    fireEvent.click(initial.getByRole('button', { name: 'Add host rule' }));

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        hostRules: [
          {
            hostPatterns: [],
            pathMatcher: { defaultService: { name: 'backend-a' }, pathRules: [] },
          },
        ],
      }),
    );

    const withMatcher = {
      ...command,
      hostRules: [
        {
          hostPatterns: ['api.example.com'],
          pathMatcher: { defaultService: { name: 'backend-a' }, pathRules: [] },
        },
      ],
    };
    initial.unmount();
    const stable = render(<GceHttpLoadBalancerEditor command={withMatcher} data={data} onChange={onChange} />);

    fireEvent.change(stable.getByTestId('default-backend-service'), { target: { value: 'backend-b' } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        defaultService: { name: 'backend-b' },
        hostRules: [
          {
            hostPatterns: ['api.example.com'],
            pathMatcher: { defaultService: { name: 'backend-a' }, pathRules: [] },
          },
        ],
      }),
    );
  });

  it('keeps unresolved resource references in every selectable resource list', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'removed-check', name: 'removed-backend' }],
        defaultService: 'removed-backend',
        healthChecks: [{ name: 'removed-check' }],
        listeners: [
          {
            certificate: 'https://compute/sslCertificates/removed-cert',
            ipAddress: 'removed-address',
            name: 'frontend',
            protocol: 'HTTPS',
          },
        ],
        loadBalancerType: 'HTTP',
        name: 'web',
      },
      'edit',
    );
    const options = buildGceHttpLoadBalancerOptions(command, {
      ...emptyData,
      addresses: [{ account: 'account-a', name: 'current-address', region: 'global' }],
      backendServices: [{ account: 'account-a', name: 'current-backend', region: 'global' }],
      certificates: [{ account: 'account-a', name: 'current-cert' }],
      healthChecks: [{ account: 'account-a', name: 'current-check', region: 'global' }],
    } as any);

    expect(options.addresses.map(({ name }) => name)).toEqual(['current-address', 'removed-address']);
    expect(options.certificates).toContainEqual(
      expect.objectContaining({ name: 'removed-cert', selfLink: 'https://compute/sslCertificates/removed-cert' }),
    );
    expect(options.healthChecks.map(({ name }) => name)).toEqual(['current-check', 'removed-check']);
    expect(options.backendServices.map(({ name }) => name)).toEqual(['current-backend', 'removed-backend']);
  });

  it('scopes Clouddriver resources by account, load-balancer location, and network while preserving selections', () => {
    const data = {
      ...emptyData,
      addresses: [
        { account: 'account-a', name: 'global-address' },
        { account: 'account-a', name: 'regional-address', region: 'europe-west1' },
        { account: 'account-a', name: 'wrong-region-address', region: 'us-central1' },
        { account: 'account-b', name: 'wrong-account-address' },
      ],
      backendServices: [
        { account: 'account-a', name: 'global-backend', region: 'global' },
        { account: 'account-a', name: 'regional-backend', region: 'europe-west1' },
        { account: 'account-a', name: 'wrong-region-backend', region: 'us-central1' },
        { account: 'account-b', name: 'wrong-account-backend', region: 'global' },
      ],
      certificates: [
        { account: 'account-a', name: 'global-certificate' },
        { account: 'account-b', name: 'wrong-account-certificate' },
      ],
      healthChecks: [
        { account: 'account-a', name: 'global-check' },
        { account: 'account-a', name: 'regional-check', region: 'europe-west1' },
        { account: 'account-a', name: 'wrong-region-check', region: 'us-central1' },
        { account: 'account-b', name: 'wrong-account-check' },
      ],
      networks: [
        { account: 'account-a', id: 'host-project/network-a', name: 'network-a', region: 'global' },
        { account: 'account-b', id: 'other-project/network-a', name: 'wrong-account-network', region: 'global' },
      ],
      subnets: [
        {
          account: 'account-a',
          name: 'subnet-a',
          network: 'host-project/network-a',
          region: 'europe-west1',
        },
        {
          account: 'account-a',
          name: 'wrong-network-subnet',
          network: 'host-project/network-b',
          region: 'europe-west1',
        },
        {
          account: 'account-a',
          name: 'wrong-region-subnet',
          network: 'host-project/network-a',
          region: 'us-central1',
        },
        {
          account: 'account-b',
          name: 'wrong-account-subnet',
          network: 'other-project/network-a',
          region: 'europe-west1',
        },
      ],
    } as any;
    const external = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'global-check', name: 'global-backend' }],
        certificate: 'global-certificate',
        ipAddress: 'global-address',
        loadBalancerType: 'HTTP',
        name: 'external',
      },
      'create',
    );
    const internal = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'removed-check', name: 'removed-backend' }],
        certificate: 'removed-certificate',
        ipAddress: 'removed-address',
        loadBalancerType: 'INTERNAL_MANAGED',
        name: 'internal',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'removed-subnet',
      },
      'edit',
    );

    const externalOptions = buildGceHttpLoadBalancerOptions(external, data);
    expect(externalOptions.addresses.map(({ name }) => name)).toEqual(['global-address']);
    expect(externalOptions.certificates.map(({ name }) => name)).toEqual(['global-certificate']);
    expect(externalOptions.healthChecks.map(({ name }) => name)).toEqual(['global-check']);
    expect(externalOptions.backendServices.map(({ name }) => name)).toEqual(['global-backend']);
    expect(externalOptions.networks.map(({ name }) => name)).toEqual(['network-a']);
    expect(externalOptions.subnets).toEqual([]);

    const internalOptions = buildGceHttpLoadBalancerOptions(internal, data);
    expect(internalOptions.addresses.map(({ name }) => name)).toEqual(['regional-address', 'removed-address']);
    expect(internalOptions.certificates.map(({ name }) => name)).toEqual(['removed-certificate']);
    expect(internalOptions.healthChecks.map(({ name }) => name)).toEqual(['regional-check', 'removed-check']);
    expect(internalOptions.backendServices.map(({ name }) => name)).toEqual(['regional-backend', 'removed-backend']);
    expect(internalOptions.networks.map(({ name }) => name)).toEqual(['network-a']);
    expect(internalOptions.subnets.map(({ name }) => name)).toEqual(['subnet-a', 'removed-subnet']);
  });

  it('locks infrastructure identity controls only while editing', () => {
    const onChange = vi.fn();
    const internal = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        loadBalancerType: 'INTERNAL_MANAGED',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'edit',
    );
    const rendered = render(<GceHttpLoadBalancerEditor command={internal} data={emptyData} onChange={onChange} />);

    ['load-balancer-name', 'load-balancer-type', 'credentials', 'region'].forEach((testId) =>
      expect(rendered.getByTestId(testId)).toBeDisabled(),
    );

    rendered.rerender(
      <GceHttpLoadBalancerEditor command={{ ...internal, mode: 'create' }} data={emptyData} onChange={onChange} />,
    );
    ['load-balancer-name', 'load-balancer-type', 'credentials', 'region'].forEach((testId) =>
      expect(rendered.getByTestId(testId)).not.toBeDisabled(),
    );
  });

  it('locks the EXTERNAL_MANAGED network while editing because existing listeners keep their network', () => {
    const onChange = vi.fn();
    const command = (loadBalancerType: 'EXTERNAL_MANAGED' | 'INTERNAL_MANAGED', mode: 'create' | 'edit') =>
      normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          loadBalancerType,
          name: 'web',
          network: 'network-a',
          region: 'europe-west1',
          subnet: 'subnet-a',
        },
        mode,
      );
    const networkSelect = (loadBalancerType: 'EXTERNAL_MANAGED' | 'INTERNAL_MANAGED', mode: 'create' | 'edit') => {
      const { getByTestId, unmount } = render(
        <GceHttpLoadBalancerEditor command={command(loadBalancerType, mode)} data={emptyData} onChange={onChange} />,
      );
      const network = getByTestId('network');
      return { network, unmount };
    };

    const externalEdit = networkSelect('EXTERNAL_MANAGED', 'edit');
    expect(externalEdit.network).toBeDisabled();
    externalEdit.unmount();
    const externalCreate = networkSelect('EXTERNAL_MANAGED', 'create');
    expect(externalCreate.network).toBeEnabled();
    externalCreate.unmount();
    const internalEdit = networkSelect('INTERNAL_MANAGED', 'edit');
    expect(internalEdit.network).toBeEnabled();
  });

  it('drops EXTERNAL_MANAGED listener addresses when the account or region changes', () => {
    const scopedCommand = (loadBalancerType: 'EXTERNAL_MANAGED' | 'INTERNAL_MANAGED') =>
      normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          listeners: [{ ipAddress: '203.0.113.10', name: 'frontend', port: 80, protocol: 'HTTP', subnet: 'subnet-a' }],
          loadBalancerType,
          name: 'web',
          network: 'network-a',
          region: 'europe-west1',
          subnet: 'subnet-a',
        },
        'create',
      );
    const onChange = vi.fn();
    const data = {
      ...emptyData,
      accounts: [{ name: 'account-a' }, { name: 'account-b' }],
      regions: [{ name: 'europe-west1' }, { name: 'us-central1' }],
    };
    const external = render(
      <GceHttpLoadBalancerEditor command={scopedCommand('EXTERNAL_MANAGED')} data={data} onChange={onChange} />,
    );

    fireEvent.change(external.getByTestId('credentials'), { target: { value: 'account-b' } });
    expect(onChange.mock.lastCall[0].credentials).toBe('account-b');
    expect(onChange.mock.lastCall[0].listeners[0].address).toBeUndefined();
    fireEvent.change(external.getByTestId('region'), { target: { value: 'us-central1' } });
    expect(onChange.mock.lastCall[0].region).toBe('us-central1');
    expect(onChange.mock.lastCall[0].listeners[0].address).toBeUndefined();
    external.unmount();

    const internal = render(
      <GceHttpLoadBalancerEditor command={scopedCommand('INTERNAL_MANAGED')} data={data} onChange={onChange} />,
    );
    fireEvent.change(internal.getByTestId('region'), { target: { value: 'us-central1' } });
    expect(onChange.mock.lastCall[0].region).toBe('us-central1');
    expect(onChange.mock.lastCall[0].listeners[0].address).toEqual({ name: '203.0.113.10' });
  });

  it('restores account, region, network, subnet, and composite type controls', () => {
    const onChange = vi.fn();
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        listeners: [{ ipAddress: 'internal-address', name: 'frontend', subnet: 'subnet-a' }],
        loadBalancerType: 'INTERNAL_MANAGED',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'create',
    );
    const rendered = render(
      <GceHttpLoadBalancerEditor
        command={command}
        data={{
          ...emptyData,
          accounts: [{ name: 'account-a' }, { name: 'account-b' }],
          networks: [{ name: 'network-a' }],
          regions: [{ name: 'europe-west1' }],
          subnets: [{ name: 'subnet-a' }],
        }}
        onChange={onChange}
      />,
    );

    expect(rendered.getByTestId('load-balancer-type')).toHaveValue('INTERNAL_MANAGED');
    expect(rendered.getByTestId('load-balancer-name')).toHaveValue('web');
    expect(rendered.getByTestId('credentials')).toHaveValue('account-a');
    expect(rendered.getByTestId('region')).toHaveValue('europe-west1');
    expect(rendered.getByTestId('network')).toHaveValue('network-a');
    expect(rendered.getByTestId('subnet')).toHaveValue('subnet-a');

    fireEvent.change(rendered.getByTestId('load-balancer-name'), { target: { value: 'web-updated' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ name: 'web-updated' }));

    fireEvent.change(rendered.getByTestId('load-balancer-type'), { target: { value: 'HTTP' } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ loadBalancerType: 'HTTP', network: undefined, region: 'global', subnet: undefined }),
    );
  });

  it('constrains EXTERNAL_MANAGED to regional scope with network and listener tier', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        listeners: [
          {
            certificate: 'regional-cert',
            ipAddress: '203.0.113.10',
            name: 'app-https',
            networkTier: 'STANDARD',
            port: 443,
            protocol: 'HTTPS',
          },
        ],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
      },
      'create',
    );

    expect(constrainGceHttpLoadBalancerCommand(command)).toEqual(
      expect.objectContaining({
        loadBalancerType: 'EXTERNAL_MANAGED',
        network: { name: 'network-a' },
        region: 'europe-west1',
        subnet: undefined,
      }),
    );
    expect(constrainGceHttpLoadBalancerCommand(command).listeners).toEqual([
      {
        certificate: { name: 'regional-cert' },
        address: { name: '203.0.113.10' },
        name: 'app-https',
        networkTier: 'STANDARD',
        portRange: '443',
        protocol: 'HTTPS',
      },
    ]);
  });

  it('retains networkTier when constraining an EXTERNAL_MANAGED plaintext HTTP listener', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        listeners: [
          {
            ipAddress: '203.0.113.10',
            name: 'app-http',
            networkTier: 'STANDARD',
            port: 80,
            protocol: 'HTTP',
          },
        ],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
      },
      'create',
    );

    expect(constrainGceHttpLoadBalancerCommand(command).listeners).toEqual([
      {
        address: { name: '203.0.113.10' },
        name: 'app-http',
        networkTier: 'STANDARD',
        portRange: '80',
        protocol: 'HTTP',
      },
    ]);
  });

  it('scopes EXTERNAL_MANAGED resources by account and region and filters proxy-only networks', () => {
    const data = {
      ...emptyData,
      addresses: [
        { account: 'account-a', address: '203.0.113.10', addressType: 'EXTERNAL', region: 'europe-west1' },
        { account: 'account-a', address: '10.0.0.1', addressType: 'INTERNAL', region: 'europe-west1' },
        { account: 'account-a', address: '198.51.100.1', addressType: 'EXTERNAL', region: 'us-central1' },
        { account: 'account-b', address: '203.0.113.11', addressType: 'EXTERNAL', region: 'europe-west1' },
      ],
      backendServices: [
        { account: 'account-a', name: 'regional-backend', region: 'europe-west1' },
        { account: 'account-a', name: 'global-backend', region: 'global' },
      ],
      certificates: [
        { account: 'account-a', name: 'regional-cert', region: 'europe-west1' },
        { account: 'account-b', name: 'wrong-account-cert', region: 'europe-west1' },
      ],
      healthChecks: [
        { account: 'account-a', name: 'regional-check', region: 'europe-west1' },
        { account: 'account-a', name: 'global-check', region: 'global' },
      ],
      networks: [
        { account: 'account-a', id: 'network-a', name: 'network-a', region: 'global' },
        { account: 'account-a', id: 'network-b', name: 'network-b', region: 'global' },
      ],
      subnets: [
        {
          account: 'account-a',
          name: 'proxy-subnet',
          network: 'network-a',
          purpose: 'REGIONAL_MANAGED_PROXY',
          region: 'europe-west1',
        },
        {
          account: 'account-a',
          name: 'internal-subnet',
          network: 'network-b',
          purpose: 'INTERNAL_HTTPS_LOAD_BALANCER',
          region: 'europe-west1',
        },
      ],
    } as any;
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'regional-check', name: 'regional-backend' }],
        certificate: 'regional-cert',
        ipAddress: '203.0.113.10',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'external',
        network: 'network-a',
        region: 'europe-west1',
      },
      'create',
    );

    const options = buildGceHttpLoadBalancerOptions(command, data);
    expect(options.addresses.filter(({ address }) => address).map(({ address }) => address)).toEqual(['203.0.113.10']);
    // The listener select value is the persisted IP, so it must stay selectable next to the named address.
    expect(options.addresses.map(({ name }) => name)).toContain('203.0.113.10');
    expect(options.certificates.map(({ name }) => name)).toEqual(['regional-cert']);
    expect(options.healthChecks.map(({ name }) => name)).toEqual(['regional-check']);
    expect(options.backendServices.map(({ name }) => name)).toEqual(['regional-backend']);
    expect(options.networks.map(({ name }) => name)).toEqual(['network-a']);
    expect(options.subnets).toEqual([]);
  });

  it('rejects Shared VPCs, unsupported backend protocols, and destructive same-name listener edits', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http', protocol: 'HTTP2' }],
        defaultService: 'backend-a',
        healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/' }],
        listeners: [{ name: 'frontend', networkTier: 'PREMIUM', port: 80, protocol: 'HTTP' }],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'web',
        network: 'host-project/shared-network',
        region: 'europe-west1',
      },
      'edit',
    );
    command.listeners[0].portRange = '8080';

    expect(validateGceHttpLoadBalancerCommand(command)).toEqual(
      expect.arrayContaining([
        'Shared VPC networks are not supported for EXTERNAL_MANAGED load balancers.',
        'Backend service protocol must be HTTP or HTTPS.',
        'Rename the listener to change its port, address, network tier, or HTTP/HTTPS protocol.',
      ]),
    );

    command.network = { name: 'local-network' };
    command.backendServices[0].protocol = 'HTTPS';
    command.listeners[0] = { ...command.listeners[0], name: 'replacement' };
    expect(validateGceHttpLoadBalancerCommand(command)).not.toEqual(
      expect.arrayContaining([
        'Shared VPC networks are not supported for EXTERNAL_MANAGED load balancers.',
        'Backend service protocol must be HTTP or HTTPS.',
        'Rename the listener to change its port, address, network tier, or HTTP/HTTPS protocol.',
      ]),
    );
  });

  it('reports a missing network for a new EXTERNAL_MANAGED load balancer instead of throwing', () => {
    const command = normalizeGceLoadBalancerCommand(
      { account: 'account-a', loadBalancerType: 'EXTERNAL_MANAGED', region: 'europe-west1' },
      'create',
    );
    command.network = undefined;

    const errors = validateGceHttpLoadBalancerCommand(command);

    expect(errors).toContain('Network is required for EXTERNAL_MANAGED load balancers.');
    expect(errors).not.toContain('Shared VPC networks are not supported for EXTERNAL_MANAGED load balancers.');
  });

  it('offers only account-local networks for EXTERNAL_MANAGED', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'web',
        region: 'europe-west1',
      },
      'create',
    );
    const options = buildGceHttpLoadBalancerOptions(command, {
      ...emptyData,
      networks: [
        { account: 'account-a', id: 'local-network', name: 'local-network' },
        { account: 'account-a', id: 'host-project/shared-network', name: 'shared-network' },
      ],
      subnets: [
        {
          account: 'account-a',
          name: 'local-proxy',
          network: 'local-network',
          purpose: 'REGIONAL_MANAGED_PROXY',
          region: 'europe-west1',
        },
        {
          account: 'account-a',
          name: 'shared-proxy',
          network: 'host-project/shared-network',
          purpose: 'REGIONAL_MANAGED_PROXY',
          region: 'europe-west1',
        },
      ],
    } as any);

    expect(options.networks.map(({ name }) => name)).toEqual(['local-network']);
  });

  it('rejects EXTERNAL_MANAGED listeners that use certificate maps', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        listeners: [{ certificateMap: 'shared-map', name: 'frontend', port: 443, protocol: 'HTTPS' }],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'web',
        network: 'network-a',
        region: 'europe-west1',
      },
      'create',
    );
    expect(validateGceHttpLoadBalancerCommand(command)).toContain(
      'Certificate maps are not supported for EXTERNAL_MANAGED load balancers.',
    );
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`strips stale networkTier when constraining ${loadBalancerType} from EXTERNAL_MANAGED`, () => {
      const external = normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          listeners: [
            {
              ipAddress: '203.0.113.10',
              name: 'frontend',
              networkTier: 'STANDARD',
              port: 443,
              protocol: 'HTTPS',
            },
          ],
          loadBalancerType: 'EXTERNAL_MANAGED',
          name: 'web',
          network: 'network-a',
          region: 'europe-west1',
        },
        'create',
      );

      const constrained = constrainGceHttpLoadBalancerCommand({
        ...external,
        loadBalancerType,
        ...(loadBalancerType === 'INTERNAL_MANAGED'
          ? { network: { name: 'network-a' }, region: 'europe-west1', subnet: { name: 'subnet-a' } }
          : { network: undefined, region: 'global', subnet: undefined }),
      });

      expect(constrained.listeners.every(({ networkTier }) => networkTier === undefined)).toBe(true);
    });
  });
});

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}
