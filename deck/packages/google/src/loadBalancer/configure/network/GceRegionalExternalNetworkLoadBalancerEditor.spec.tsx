import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import {
  buildGceRegionalExternalNetworkLoadBalancerOptions,
  GceRegionalExternalNetworkLoadBalancerEditor,
  validateGceRegionalExternalNetworkLoadBalancerCommand,
} from './GceRegionalExternalNetworkLoadBalancerEditor';
import { normalizeGceRegionalExternalNetworkLoadBalancerCommand } from './GceRegionalExternalNetworkLoadBalancerModal';
import type { IGceLoadBalancerData } from '../common';

describe('GceRegionalExternalNetworkLoadBalancerEditor', () => {
  const emptyData = (): IGceLoadBalancerData => ({
    accounts: [],
    addresses: [],
    backendServices: [],
    certificates: [],
    healthChecks: [],
    networks: [],
    regions: [],
    subnets: [],
  });

  it('filters external addresses and stores selected IP and network tier', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const options = buildGceRegionalExternalNetworkLoadBalancerOptions(command, {
      ...emptyData(),
      addresses: [
        {
          account: 'account-a',
          address: '35.1.2.3',
          addressType: 'EXTERNAL',
          networkTier: 'PREMIUM',
          region: 'europe-west1',
        },
        {
          account: 'account-a',
          address: '10.0.0.1',
          addressType: 'INTERNAL',
          networkTier: 'PREMIUM',
          region: 'europe-west1',
        },
        { account: 'account-a', address: '198.51.100.1', addressType: 'EXTERNAL', region: 'us-central1' },
      ],
    } as any);

    expect(options.addresses.map(({ address }) => address)).toEqual(['35.1.2.3']);

    const onChange = vi.fn();
    const { getByLabelText } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor
        command={command}
        data={
          {
            ...emptyData(),
            addresses: [{ address: '35.1.2.3', addressType: 'EXTERNAL', networkTier: 'PREMIUM' }],
          } as any
        }
        onChange={onChange}
      />,
    );

    expect(optionValues(getByLabelText('IP address'))).toEqual(['', '35.1.2.3']);
    fireEvent.change(getByLabelText('IP address'), { target: { value: '35.1.2.3' } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        listeners: [
          expect.objectContaining({ address: expect.objectContaining({ address: '35.1.2.3', name: '35.1.2.3' }) }),
        ],
        networkTier: 'PREMIUM',
      }),
    );
  });

  it('renders region, protocol, discrete ports, health check, and session affinity controls', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP',
        },
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        ports: ['80', '443'],
        region: 'europe-west1',
      },
      'edit',
    );
    const { container, getByLabelText, getByRole } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={vi.fn()} />,
    );

    ['Account', 'Region', 'IP address', 'Network tier', 'Protocol', 'Ports', 'Session affinity'].forEach((label) =>
      expect(getByLabelText(label)).toBeInTheDocument(),
    );
    expect(getByRole('heading', { name: 'Health Check' })).toBeInTheDocument();
    expect(controls(container).healthCheckName).toHaveValue('tcp-check');
    expect(optionValues(getByLabelText('Protocol'))).toEqual(['TCP', 'UDP']);
    expect(optionValues(getByLabelText('Session affinity'))).toEqual([
      'NONE',
      'CLIENT_IP',
      'CLIENT_IP_PROTO',
      'CLIENT_IP_PORT_PROTO',
    ]);
  });

  it('updates both health check references by name without mutating the command', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'old-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        loadBalancerName: 'app-main',
        ports: ['80'],
        region: 'europe-west1',
      },
      'edit',
    );
    const onChange = vi.fn();
    const { container } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    fireEvent.change(controls(container).healthCheckName, { target: { value: 'new-check' } });

    const nextCommand = onChange.mock.lastCall[0];
    expect(nextCommand.backendServices[0].healthCheck).toBe(nextCommand.healthChecks[0]);
    expect(nextCommand.backendServices[0].healthCheck.name).toBe('new-check');
    expect(nextCommand.healthChecks[0].name).toBe('new-check');
    expect((command.backendServices[0].healthCheck as any).name).toBe('old-check');
    expect(command.healthChecks[0].name).toBe('old-check');
  });

  it('updates protocol, raw ports, and session affinity through editor changes', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const onChange = vi.fn();
    const { getByLabelText } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    fireEvent.change(getByLabelText('Protocol'), { target: { value: 'UDP' } });
    expect(onChange.mock.lastCall[0].listeners[0].protocol).toBe('UDP');

    fireEvent.change(getByLabelText('Ports'), { target: { value: '80, 443 , 8080' } });
    expect(onChange.mock.lastCall[0].ports).toEqual(['80', ' 443 ', ' 8080']);

    fireEvent.change(getByLabelText('Session affinity'), { target: { value: 'CLIENT_IP_PORT_PROTO' } });
    expect(onChange.mock.lastCall[0].backendServices[0].sessionAffinity).toBe('CLIENT_IP_PORT_PROTO');
  });

  it('locks identity and forwarding-rule fields while allowing backend edits', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['80'],
        region: 'europe-west1',
      },
      'edit',
    );
    const { container } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={vi.fn()} />,
    );
    const fields = controls(container);

    (['name', 'credentials', 'region', 'address', 'networkTier', 'protocol', 'ports'] as const).forEach((field) =>
      expect(fields[field], field).toBeDisabled(),
    );
    (['sessionAffinity', 'healthCheckName'] as const).forEach((field) => expect(fields[field], field).toBeEnabled());
  });

  it('drops the selected address when the account or region changes', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', ipAddress: '35.1.2.3', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const onChange = vi.fn();
    const { getByLabelText } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor
        command={command}
        data={{
          ...emptyData(),
          accounts: [{ name: 'account-a' }, { name: 'account-b' }],
          regions: [{ name: 'europe-west1' }, { name: 'us-central1' }],
        }}
        onChange={onChange}
      />,
    );

    fireEvent.change(getByLabelText('Account'), { target: { value: 'account-b' } });
    expect(onChange.mock.lastCall[0].credentials).toBe('account-b');
    expect(onChange.mock.lastCall[0].listeners[0].address).toBeUndefined();

    fireEvent.change(getByLabelText('Region'), { target: { value: 'us-central1' } });
    expect(onChange.mock.lastCall[0].region).toBe('us-central1');
    expect(onChange.mock.lastCall[0].listeners[0].address).toBeUndefined();
  });

  it('lets an ephemeral address choose its network tier and derives it from a reserved address', () => {
    const onChange = vi.fn();
    const ephemeral = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const ephemeralRender = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={ephemeral} data={emptyData()} onChange={onChange} />,
    );
    const ephemeralTier = ephemeralRender.getByLabelText('Network tier');

    expect(ephemeralTier).toBeEnabled();
    expect(optionValues(ephemeralTier)).toEqual(['PREMIUM', 'STANDARD']);
    fireEvent.change(ephemeralTier, { target: { value: 'STANDARD' } });
    expect(onChange.mock.lastCall[0].networkTier).toBe('STANDARD');
    ephemeralRender.unmount();

    const reserved = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', ipAddress: '35.1.2.3', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const { getByLabelText } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={reserved} data={emptyData()} onChange={onChange} />,
    );
    expect(getByLabelText('Network tier')).toBeDisabled();
  });

  it('associates every field label with its control', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const { container } = render(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={vi.fn()} />,
    );

    const labels = Array.from(container.querySelectorAll('label'));
    expect(labels.length).toBeGreaterThan(0);
    labels.forEach((label) => {
      expect(label.htmlFor).toBeTruthy();
      expect(container.querySelectorAll(`#${label.htmlFor}`)).toHaveLength(1);
      expect(label.control, label.textContent).not.toBeNull();
    });
  });

  it('validates required discrete ports, protocol, health check, and supported session affinity', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: '',
        backendService: { name: '', sessionAffinity: 'GENERATED_COOKIE' },
        loadBalancerName: '',
        ports: ['70000', 'abc'],
        region: '',
      },
      'create',
    );

    expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toEqual([
      'Name is required.',
      'Account is required.',
      'Region is required.',
      'Ports must be between 1 and 65535.',
      'Backend service name is required.',
      'Each backend service requires a health check.',
      'Health check name is required.',
      'Health check port must be between 1 and 65535.',
      'Session affinity must be NONE, CLIENT_IP, CLIENT_IP_PROTO, or CLIENT_IP_PORT_PROTO.',
    ]);
  });

  it('accepts every supported passthrough session affinity', () => {
    (['NONE', 'CLIENT_IP', 'CLIENT_IP_PROTO', 'CLIENT_IP_PORT_PROTO'] as const).forEach((sessionAffinity) => {
      const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
        {
          account: 'account-a',
          backendService: {
            healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
            name: 'app-main',
            sessionAffinity,
          },
          loadBalancerName: 'app-main',
          ports: ['80'],
          region: 'europe-west1',
        },
        'create',
      );

      expect(
        validateGceRegionalExternalNetworkLoadBalancerCommand(command).some((error) =>
          error.startsWith('Session affinity'),
        ),
      ).toBe(false);
    });
  });

  (['edit', 'pipeline'] as const).forEach((mode) => {
    it(`rejects an explicitly null ${mode} health check without throwing`, () => {
      const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
        {
          account: 'account-a',
          backendService: {
            healthCheck: null,
            name: 'app-main',
            sessionAffinity: 'NONE',
          },
          loadBalancerName: 'app-main',
          ports: ['80'],
          region: 'europe-west1',
        },
        mode,
      );

      expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toEqual([
        'Each backend service requires a health check.',
        'Health check name is required.',
        'Health check port must be between 1 and 65535.',
      ]);
    });
  });

  it('rejects a health check whose name is omitted', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        loadBalancerName: 'app-main',
        ports: ['80'],
        region: 'europe-west1',
      },
      'create',
    );
    command.backendServices[0].healthCheck = { healthCheckType: 'TCP', name: '', port: 80 };
    command.healthChecks = [{ healthCheckType: 'TCP', name: '', port: 80 }];

    expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toContain('Health check name is required.');
  });

  it('rejects more than five discrete ports', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        loadBalancerName: 'app-main',
        ports: ['1', '2', '3', '4', '5', '6'],
        region: 'europe-west1',
      },
      'create',
    );

    expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toContain(
      'REGIONAL_EXTERNAL_NETWORK load balancers accept between one and five ports.',
    );
  });

  it('rejects non-lexical ports, unsupported protocols, and destructive edit changes', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        ports: ['80'],
        region: 'europe-west1',
      },
      'edit',
    );

    ['0', '65536', '1.5', '1e2', ' 80', '80 ', '', 'abc'].forEach((port) => {
      command.ports = [port];
      expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toContain(
        'Ports must be between 1 and 65535.',
      );
    });

    command.ports = ['80'];
    command.listeners[0].protocol = 'HTTP';
    expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toEqual(
      expect.arrayContaining([
        'Protocol must be TCP or UDP.',
        'Protocol and ports cannot be changed while editing a REGIONAL_EXTERNAL_NETWORK load balancer.',
      ]),
    );

    const healthCheck = { ...command.healthChecks[0], port: '1e2' } as any;
    command.healthChecks = [healthCheck];
    command.backendServices[0].healthCheck = healthCheck;
    expect(validateGceRegionalExternalNetworkLoadBalancerCommand(command)).toContain(
      'Health check port must be between 1 and 65535.',
    );
  });
});

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}

function controls(container: HTMLElement) {
  const { getAllByLabelText, getByLabelText } = within(container);
  const [name, healthCheckName] = getAllByLabelText('Name');
  return {
    address: getByLabelText('IP address'),
    credentials: getByLabelText('Account'),
    healthCheckName,
    name,
    networkTier: getByLabelText('Network tier'),
    ports: getByLabelText('Ports'),
    protocol: getByLabelText('Protocol'),
    region: getByLabelText('Region'),
    sessionAffinity: getByLabelText('Session affinity'),
  };
}
