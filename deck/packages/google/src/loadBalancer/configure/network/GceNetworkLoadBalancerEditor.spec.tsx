import React from 'react';
import { shallow } from 'enzyme';

import type { IGceLoadBalancerData } from '../common';

import {
  buildGceNetworkLoadBalancerOptions,
  GceNetworkLoadBalancerEditor,
  validateGceNetworkLoadBalancerCommand,
} from './GceNetworkLoadBalancerEditor';
import { normalizeGceNetworkLoadBalancerCommand } from './GceNetworkLoadBalancerModal';

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
    const wrapper = shallow(
      <GceNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={jasmine.createSpy('onChange')} />,
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
    ].forEach((field) => expect(wrapper.find(`[data-field="${field}"]`).exists()).toBe(true));
    ['name', 'credentials', 'region', 'network', 'targetPool', 'sessionAffinity'].forEach((field) => {
      const control = wrapper.find(`[data-field="${field}"]`);
      expect((control.find('input').exists() ? control.find('input') : control.find('select')).prop('disabled')).toBe(
        true,
      );
    });
    ['address', 'protocol', 'portRange', 'healthCheckPort'].forEach((field) => {
      const control = wrapper.find(`[data-field="${field}"]`);
      expect(
        (control.find('input').exists() ? control.find('input') : control.find('select')).prop('disabled'),
      ).not.toBe(true);
    });
    expect(wrapper.find('[data-field="protocol"] option').map((option) => option.prop('value'))).toEqual([
      '',
      'TCP',
      'UDP',
    ]);
    expect(wrapper.find('[data-field="sessionAffinity"] option').map((option) => option.prop('value'))).toEqual([
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
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(<GceNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />);

    wrapper.find('[data-field="protocol"] select').simulate('change', { target: { value: 'UDP' } });
    expect(onChange.calls.mostRecent().args[0].listeners[0].protocol).toBe('UDP');

    wrapper.find('[data-field="sessionAffinity"] select').simulate('change', {
      target: { value: 'CLIENT_IP_PROTO' },
    });
    expect(onChange.calls.mostRecent().args[0].sessionAffinity).toBe('CLIENT_IP_PROTO');

    wrapper.find('[data-field="requestPath"] input').simulate('change', { target: { value: 'status' } });
    expect(onChange.calls.mostRecent().args[0].healthChecks[0].requestPath).toBe('/status');

    wrapper.find('[data-field="healthCheckEnabled"] input').simulate('change', { target: { checked: false } });
    expect(onChange.calls.mostRecent().args[0].healthChecks).toEqual([]);
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
