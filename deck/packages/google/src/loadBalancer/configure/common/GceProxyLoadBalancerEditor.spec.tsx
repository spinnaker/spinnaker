import React from 'react';
import { shallow } from 'enzyme';

import { normalizeGceLoadBalancerCommand } from './gceLoadBalancerModels';
import {
  applyGceProxyTypeConstraints,
  GCE_PROXY_TYPE_CONFIG,
  GceProxyLoadBalancerEditor,
  getGceProxyResourceOptions,
  validateGceProxyLoadBalancerCommand,
} from './GceProxyLoadBalancerEditor';

describe('GceProxyLoadBalancerEditor', () => {
  it('defines frontend and backend protocols that match each Clouddriver proxy contract', () => {
    expect(GCE_PROXY_TYPE_CONFIG.INTERNAL.frontendProtocols).toEqual(['TCP', 'UDP']);
    expect(GCE_PROXY_TYPE_CONFIG.INTERNAL.backendProtocols).toEqual(['TCP', 'UDP']);
    expect(GCE_PROXY_TYPE_CONFIG.TCP.frontendProtocols).toEqual(['TCP']);
    expect(GCE_PROXY_TYPE_CONFIG.TCP.backendProtocols).toEqual(['TCP']);
    expect(GCE_PROXY_TYPE_CONFIG.SSL.frontendProtocols).toEqual(['SSL']);
    expect(GCE_PROXY_TYPE_CONFIG.SSL.backendProtocols).toEqual(['TCP']);
  });

  it('applies SSL frontend, backend, global-region, and certificate constraints without dropping settings', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [
          {
            affinityCookieTtlSec: 300,
            connectionDrainingTimeoutSec: 45,
            name: 'backend-a',
            portName: 'https',
            protocol: 'UDP',
            sessionAffinity: 'GENERATED_COOKIE',
          },
        ],
        certificate: 'cert-a',
        loadBalancerType: 'SSL',
        name: 'app-main',
        portRange: '443',
        protocol: 'TCP',
        region: 'europe-west1',
      },
      'edit',
    );

    const constrained = applyGceProxyTypeConstraints(command);

    expect(constrained.region).toBe('global');
    expect(constrained.listeners[0].protocol).toBe('SSL');
    expect(constrained.listeners[0].certificate).toEqual({ name: 'cert-a' });
    expect(constrained.backendServices[0]).toEqual(
      jasmine.objectContaining({
        affinityCookieTtlSec: 300,
        connectionDrainingTimeoutSec: 45,
        portName: 'https',
        protocol: 'TCP',
        sessionAffinity: 'GENERATED_COOKIE',
      }),
    );
  });

  it('limits INTERNAL UDP to no session affinity while retaining regional network settings', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ name: 'backend-a', protocol: 'UDP', sessionAffinity: 'CLIENT_IP' }],
        ipProtocol: 'UDP',
        loadBalancerType: 'INTERNAL',
        name: 'app-main',
        network: 'network-a',
        ports: ['80', '443'],
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'create',
    );

    const constrained = applyGceProxyTypeConstraints(command);

    expect(constrained.region).toBe('europe-west1');
    expect(constrained.listeners[0].protocol).toBe('UDP');
    expect(constrained.listeners[0].portRange).toBe('80,443');
    expect(constrained.backendServices[0].protocol).toBe('UDP');
    expect(constrained.backendServices[0].sessionAffinity).toBe('NONE');
    expect(constrained.network).toEqual({ name: 'network-a' });
    expect(constrained.subnet).toEqual({ name: 'subnet-a' });
  });

  it('keeps persisted unavailable references in account and location-scoped options', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'removed-check', name: 'removed-backend' }],
        certificate: 'removed-cert',
        ipAddress: 'removed-address',
        loadBalancerType: 'SSL',
        name: 'app-main',
      },
      'edit',
    );

    const options = getGceProxyResourceOptions(applyGceProxyTypeConstraints(command), {
      accounts: [{ name: 'account-a' }, { name: 'account-b' }],
      addresses: [
        { name: 'address-a', account: 'account-a', region: 'global' },
        { name: 'address-b', account: 'account-b', region: 'global' },
      ],
      backendServices: [{ name: 'backend-a', account: 'account-a', region: 'global' }],
      certificates: [{ name: 'cert-a', account: 'account-a' }],
      healthChecks: [{ name: 'check-a', account: 'account-a' }],
      networks: [],
      regions: [{ name: 'global' }],
      subnets: [],
    } as any);

    expect(options.addresses.map(({ name }) => name)).toEqual(['address-a', 'removed-address']);
    expect(options.backendServices.map(({ name }) => name)).toEqual(['backend-a', 'removed-backend']);
    expect(options.certificates.map(({ name }) => name)).toEqual(['cert-a', 'removed-cert']);
    expect(options.healthChecks.map(({ name }) => name)).toEqual(['check-a', 'removed-check']);
  });

  it('validates required common, INTERNAL, SSL, port, health-check, timeout, and backend settings', () => {
    const invalid = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        {
          account: '',
          backendServices: [
            {
              affinityCookieTtlSec: 90000,
              connectionDrainingTimeoutSec: -1,
              name: 'backend-a',
              portName: '',
              sessionAffinity: 'GENERATED_COOKIE',
            },
          ],
          healthChecks: [
            {
              checkIntervalSec: 0,
              healthyThreshold: 0,
              healthCheckType: 'HTTP',
              name: 'check-a',
              port: 70000,
              requestPath: '',
              timeoutSec: -1,
              unhealthyThreshold: 0,
            },
          ],
          loadBalancerType: 'SSL',
          name: '',
          portRange: '80',
        },
        'create',
      ),
    );
    invalid.backendServices[0].name = '';
    invalid.healthChecks[0].name = '';

    expect(validateGceProxyLoadBalancerCommand(invalid)).toEqual([
      'Name is required.',
      'Account is required.',
      'SSL proxy port must be one of 25, 43, 110, 143, 195, 443, 465, 587, 700, 993, or 995.',
      'Certificate is required for SSL proxy load balancers.',
      'Backend service is required.',
      'Port name is required.',
      'Connection draining timeout must be zero or greater.',
      'Generated cookie TTL must be between 0 and 86400 seconds.',
      'Health check name is required.',
      'Health check port must be between 1 and 65535.',
      'HTTP and HTTPS health checks require a request path.',
      'Health check timeout must be zero or greater.',
      'Health check interval must be greater than zero.',
      'Healthy threshold must be greater than zero.',
      'Unhealthy threshold must be greater than zero.',
    ]);

    const internal = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          backendServices: [{ name: 'backend-a', sessionAffinity: 'NONE' }],
          healthChecks: [{ healthCheckType: 'TCP', name: 'check-a', port: 80 }],
          loadBalancerType: 'INTERNAL',
          name: 'app-main',
          ports: ['1', '2', '3', '4', '5', '6'],
          region: '',
        },
        'create',
      ),
    );
    expect(validateGceProxyLoadBalancerCommand(internal)).toEqual([
      'Region is required.',
      'INTERNAL load balancers accept between one and five listener ports.',
      'Network is required for INTERNAL load balancers.',
      'Subnet is required for INTERNAL load balancers.',
    ]);
  });

  it('renders SSL certificates and proxy backend settings but not INTERNAL-only network fields', () => {
    const command = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        { account: 'account-a', loadBalancerType: 'SSL', name: 'app-main', portRange: '443' },
        'create',
      ),
    );
    const wrapper = shallow(
      <GceProxyLoadBalancerEditor command={command} data={emptyData()} onChange={jasmine.createSpy('onChange')} />,
    );

    expect(wrapper.find('[data-field="certificate"]').exists()).toBe(true);
    expect(wrapper.find('[data-field="portName"]').exists()).toBe(true);
    expect(wrapper.find('[data-field="connectionDrainingTimeoutSec"]').exists()).toBe(true);
    expect(wrapper.find('[data-field="network"]').exists()).toBe(false);
    expect(wrapper.find('[data-field="subnet"]').exists()).toBe(false);
  });

  it('renders regional network and subnet fields for INTERNAL without unsupported draining controls', () => {
    const command = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        { account: 'account-a', loadBalancerType: 'INTERNAL', name: 'app-main', region: 'europe-west1' },
        'create',
      ),
    );
    const wrapper = shallow(
      <GceProxyLoadBalancerEditor command={command} data={emptyData()} onChange={jasmine.createSpy('onChange')} />,
    );

    expect(wrapper.find('[data-field="network"]').exists()).toBe(true);
    expect(wrapper.find('[data-field="subnet"]').exists()).toBe(true);
    expect(wrapper.find('[data-field="connectionDrainingTimeoutSec"]').exists()).toBe(false);
    expect(wrapper.find('[data-field="certificate"]').exists()).toBe(false);
  });

  it('keeps generated listener and backend service names aligned when the load balancer name changes', () => {
    const command = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        { account: 'account-a', loadBalancerType: 'TCP', name: 'app', portRange: '443' },
        'create',
      ),
    );
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(<GceProxyLoadBalancerEditor command={command} data={emptyData()} onChange={onChange} />);

    wrapper.find('[data-field="name"] input').simulate('change', { target: { value: 'app-main' } });

    const updated = onChange.calls.mostRecent().args[0];
    expect(updated.name).toBe('app-main');
    expect(updated.listeners[0].name).toBe('app-main');
    expect(updated.backendServices[0].name).toBe('app-main');
  });

  it('retains the reserved address literal from the selected reader resource', () => {
    const command = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        { account: 'account-a', loadBalancerType: 'TCP', name: 'app-main', portRange: '443' },
        'create',
      ),
    );
    const onChange = jasmine.createSpy('onChange');
    const data = {
      ...emptyData(),
      addresses: [
        {
          account: 'account-a',
          address: '203.0.113.10',
          name: 'reserved-address',
          region: 'global',
          selfLink: 'projects/test/global/addresses/reserved-address',
        },
      ],
    };
    const wrapper = shallow(<GceProxyLoadBalancerEditor command={command} data={data} onChange={onChange} />);

    wrapper.find('[data-field="address"] select').simulate('change', { target: { value: 'reserved-address' } });

    expect(onChange.calls.mostRecent().args[0].listeners[0].address).toEqual(data.addresses[0]);
  });

  it('replaces stale inline state with the complete normalized reader health check', () => {
    const command = applyGceProxyTypeConstraints(
      normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          backendServices: [{ healthCheck: 'old-check', name: 'app-main' }],
          healthChecks: [{ healthCheckType: 'TCP', name: 'old-check', port: 80 }],
          loadBalancerType: 'TCP',
          name: 'app-main',
          portRange: '443',
        },
        'edit',
      ),
    );
    const onChange = jasmine.createSpy('onChange');
    const data = {
      ...emptyData(),
      healthChecks: [
        {
          account: 'account-a',
          checkIntervalSec: '15',
          healthCheckType: 'http',
          healthyThreshold: '2',
          host: 'api.internal',
          kind: 'healthCheck',
          name: 'new-check',
          port: '8080',
          proxyHeader: 'PROXY_V1',
          requestPath: 'ready',
          selfLink: 'projects/test/global/healthChecks/new-check',
          timeoutSec: '5',
          unhealthyThreshold: '3',
          useServingPort: false,
        },
      ],
    };
    const wrapper = shallow(<GceProxyLoadBalancerEditor command={command} data={data} onChange={onChange} />);

    wrapper.find('[data-field="healthCheck"] select').simulate('change', { target: { value: 'new-check' } });

    const updated = onChange.calls.mostRecent().args[0];
    expect(updated.backendServices[0].healthCheck).toEqual({
      name: 'new-check',
      selfLink: 'projects/test/global/healthChecks/new-check',
    });
    expect(updated.healthChecks).toEqual([
      jasmine.objectContaining({
        checkIntervalSec: 15,
        healthCheckType: 'HTTP',
        healthyThreshold: 2,
        host: 'api.internal',
        name: 'new-check',
        port: 8080,
        proxyHeader: 'PROXY_V1',
        requestPath: '/ready',
        timeoutSec: 5,
        unhealthyThreshold: 3,
        useServingPort: false,
      }),
    ]);
  });
});

function emptyData(): any {
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
