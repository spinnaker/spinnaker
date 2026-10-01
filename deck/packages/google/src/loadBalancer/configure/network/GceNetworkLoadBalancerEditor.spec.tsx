import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import {
  buildGceNetworkLoadBalancerOptions,
  GceNetworkLoadBalancerEditor,
  validateGceNetworkLoadBalancerCommand,
} from './GceNetworkLoadBalancerEditor';
import { normalizeGceNetworkLoadBalancerCommand } from './GceNetworkLoadBalancerModal';
import type { IGceLoadBalancerData } from '../common';

describe('GceNetworkLoadBalancerEditor', () => {
  it('keeps unavailable persisted address and network references in scoped options', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        ipAddress: 'projects/test/regions/europe-west1/addresses/removed-address',
        loadBalancerName: 'app-main',
        network: 'projects/test/global/networks/removed-network',
        region: 'europe-west1',
      },
      'edit',
    );
    const options = buildGceNetworkLoadBalancerOptions(command, {
      ...emptyData(),
      accounts: [{ name: 'account-a' }, { name: 'account-b' }],
      addresses: [
        { name: 'address-a', account: 'account-a', region: 'europe-west1', network: 'current-network' },
        { name: 'wrong-account', account: 'account-b', region: 'europe-west1' },
        { name: 'wrong-region', account: 'account-a', region: 'us-central1' },
      ],
      networks: [{ name: 'current-network', account: 'account-a' }],
    } as any);

    expect(options.addresses.map(({ name }) => name)).toEqual(['address-a', 'removed-address']);
    expect(options.networks.map(({ name }) => name)).toEqual(['current-network', 'removed-network']);
    expect(options.addresses[1]).toEqual({
      name: 'removed-address',
      selfLink: 'projects/test/regions/europe-west1/addresses/removed-address',
    });
  });

  it('renders all supported NETWORK fields and locks only identity, location, target pool, and edit affinity', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        healthCheck: { port: 80, requestPath: '/health' },
        ipAddress: 'address-a',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        network: 'network-a',
        portRange: '80-81',
        region: 'europe-west1',
        sessionAffinity: 'CLIENT_IP',
        targetPool: 'projects/test/regions/europe-west1/targetPools/app-main-tp',
      },
      'edit',
    );
    const { container } = render(
      <GceNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={vi.fn()} />,
    );

    [
      'name',
      'credentials',
      'region',
      'network',
      'address',
      'protocol',
      'portRange',
      'targetPool',
      'sessionAffinity',
      'healthCheckEnabled',
      'healthCheckPort',
      'requestPath',
      'timeoutSec',
      'checkIntervalSec',
      'healthyThreshold',
      'unhealthyThreshold',
    ].forEach((name) => expect(field(container, name)).toBeInTheDocument());
    ['name', 'credentials', 'region', 'network', 'targetPool', 'sessionAffinity'].forEach((name) => {
      expect(fieldControl(container, name)).toBeDisabled();
    });
    ['address', 'protocol', 'portRange', 'healthCheckPort'].forEach((name) => {
      expect(fieldControl(container, name)).not.toBeDisabled();
    });
    expect(optionValues(fieldControl(container, 'protocol'))).toEqual(['', 'TCP', 'UDP']);
    expect(optionValues(fieldControl(container, 'sessionAffinity'))).toEqual([
      '',
      'NONE',
      'CLIENT_IP',
      'CLIENT_IP_PROTO',
    ]);
  });

  it('updates listener, session affinity, and health-check state through normalized editor changes', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const onChange = vi.fn();
    const { container } = render(
      <GceNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    fireEvent.change(fieldControl(container, 'protocol'), { target: { value: 'UDP' } });
    expect(onChange.mock.lastCall[0].listeners[0].protocol).toBe('UDP');

    fireEvent.change(fieldControl(container, 'sessionAffinity'), {
      target: { value: 'CLIENT_IP_PROTO' },
    });
    expect(onChange.mock.lastCall[0].sessionAffinity).toBe('CLIENT_IP_PROTO');

    fireEvent.change(fieldControl(container, 'requestPath'), { target: { value: 'status' } });
    expect(onChange.mock.lastCall[0].healthChecks[0].requestPath).toBe('/status');

    fireEvent.click(fieldControl(container, 'healthCheckEnabled'));
    expect(onChange.mock.lastCall[0].healthChecks).toEqual([]);
  });

  it('validates the exact required listener and enabled health-check fields', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: '',
        healthCheck: {
          checkIntervalSec: 0,
          healthyThreshold: 0,
          port: 70000,
          requestPath: '',
          timeoutSec: -1,
          unhealthyThreshold: 0,
        },
        loadBalancerName: '',
        portRange: '70000-1',
        region: '',
      },
      'create',
    );

    expect(validateGceNetworkLoadBalancerCommand(command)).toEqual([
      'Name is required.',
      'Account is required.',
      'Region is required.',
      'Port range must contain ports between 1 and 65535.',
      'Health check port must be between 1 and 65535.',
      'Health check path is required.',
      'Health check timeout must be zero or greater.',
      'Health check interval must be greater than zero.',
      'Healthy threshold must be greater than zero.',
      'Unhealthy threshold must be greater than zero.',
    ]);
  });
});

function emptyData(): IGceLoadBalancerData {
  return {
    accounts: [],
    addresses: [],
    backendServices: [],
    certificates: [],
    healthChecks: [],
    networks: [],
    regions: [],
    subnets: [],
  };
}

function field(container: HTMLElement, name: string): HTMLElement | null {
  return container.querySelector(`[data-field="${name}"]`);
}

function fieldControl(container: HTMLElement, name: string): HTMLInputElement | HTMLSelectElement {
  const control = field(container, name)?.querySelector('input, select');
  if (!(control instanceof HTMLInputElement || control instanceof HTMLSelectElement)) {
    throw new Error(`No control found for ${name}`);
  }
  return control;
}

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}
