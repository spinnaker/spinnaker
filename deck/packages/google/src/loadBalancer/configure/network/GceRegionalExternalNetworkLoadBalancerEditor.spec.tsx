import React from 'react';
import { shallow } from 'enzyme';

import type { IGceLoadBalancerData } from '../common';

import {
  buildGceRegionalExternalNetworkLoadBalancerOptions,
  GceRegionalExternalNetworkLoadBalancerEditor,
  validateGceRegionalExternalNetworkLoadBalancerCommand,
} from './GceRegionalExternalNetworkLoadBalancerEditor';
import { normalizeGceRegionalExternalNetworkLoadBalancerCommand } from './GceRegionalExternalNetworkLoadBalancerModal';

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

    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    wrapper.find('[data-field="address"] select').simulate('change', {
      target: { value: '35.1.2.3' },
    });

    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({
        listeners: [jasmine.objectContaining({ address: { address: '35.1.2.3', name: '35.1.2.3' } })],
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
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerEditor
        command={command}
        data={emptyData()}
        onChange={jasmine.createSpy()}
      />,
    );

    [
      'credentials',
      'region',
      'address',
      'networkTier',
      'protocol',
      'ports',
      'sessionAffinity',
      'healthCheck',
      'healthCheckName',
    ].forEach((field) => expect(wrapper.find(`[data-field="${field}"]`).exists()).toBe(true));
    expect(wrapper.find('[data-field="protocol"] option').map((option) => option.prop('value'))).toEqual([
      'TCP',
      'UDP',
    ]);
    expect(wrapper.find('[data-field="sessionAffinity"] option').map((option) => option.prop('value'))).toEqual([
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
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    wrapper.find('[data-field="healthCheckName"] input').simulate('change', { target: { value: 'new-check' } });

    const nextCommand = onChange.calls.mostRecent().args[0];
    expect(nextCommand.backendServices[0].healthCheck).toBe(nextCommand.healthChecks[0]);
    expect(nextCommand.backendServices[0].healthCheck.name).toBe('new-check');
    expect(nextCommand.healthChecks[0].name).toBe('new-check');
    expect((command.backendServices[0].healthCheck as any).name).toBe('old-check');
    expect(command.healthChecks[0].name).toBe('old-check');
  });

  it('updates protocol, trimmed ports, and session affinity through normalized editor changes', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      { account: 'account-a', loadBalancerName: 'app-main', region: 'europe-west1' },
      'create',
    );
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />,
    );

    wrapper.find('[data-field="protocol"] select').simulate('change', { target: { value: 'UDP' } });
    expect(onChange.calls.mostRecent().args[0].listeners[0].protocol).toBe('UDP');

    wrapper.find('[data-field="ports"] input').simulate('change', { target: { value: '80, 443 , 8080' } });
    expect(onChange.calls.mostRecent().args[0].ports).toEqual(['80', '443', '8080']);

    wrapper.find('[data-field="sessionAffinity"] select').simulate('change', {
      target: { value: 'CLIENT_IP_PORT_PROTO' },
    });
    expect(onChange.calls.mostRecent().args[0].backendServices[0].sessionAffinity).toBe('CLIENT_IP_PORT_PROTO');
  });

  it('locks identity, region, and preserved edit fields while allowing protocol and port edits', () => {
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
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerEditor
        command={command}
        data={emptyData()}
        onChange={jasmine.createSpy()}
      />,
    );

    ['name', 'credentials', 'region', 'address', 'networkTier'].forEach((field) => {
      const control = wrapper.find(`[data-field="${field}"]`);
      expect((control.find('input').exists() ? control.find('input') : control.find('select')).prop('disabled')).toBe(
        true,
      );
    });
    ['protocol', 'ports', 'sessionAffinity'].forEach((field) => {
      const control = wrapper.find(`[data-field="${field}"]`);
      expect(
        (control.find('input').exists() ? control.find('input') : control.find('select')).prop('disabled'),
      ).not.toBe(true);
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
});
