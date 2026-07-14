import React from 'react';
import { shallow } from 'enzyme';

import type { IGceLoadBalancerData } from '../common';
import { normalizeGceLoadBalancerCommand } from '../common';

import * as editorModule from './GceHttpLoadBalancerEditor';
import {
  buildGceHttpLoadBalancerOptions,
  constrainGceHttpLoadBalancerCommand,
  GceHttpLoadBalancerEditor,
} from './GceHttpLoadBalancerEditor';
import { GceHttpLoadBalancerListenerEditor } from './GceHttpLoadBalancerListenerEditor';

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
      jasmine.objectContaining({
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
    const onChange = jasmine.createSpy('onChange');
    const httpCommand = normalizeGceLoadBalancerCommand(
      { account: 'account-a', listeners: [{ name: 'frontend', port: 80 }], loadBalancerType: 'HTTP', name: 'web' },
      'create',
    );
    const http = shallow(<GceHttpLoadBalancerEditor command={httpCommand} data={emptyData} onChange={onChange} />);

    const httpListener = shallow(http.find(GceHttpLoadBalancerListenerEditor).getElement());
    expect(
      httpListener.find('[data-testid="listener-protocol"] option').map((option) => option.prop('value')),
    ).toEqual(['HTTP', 'HTTPS']);

    const internal = shallow(
      <GceHttpLoadBalancerEditor
        command={{ ...httpCommand, loadBalancerType: 'INTERNAL_MANAGED', region: 'europe-west1' }}
        data={emptyData}
        onChange={onChange}
      />,
    );

    const internalListener = shallow(internal.find(GceHttpLoadBalancerListenerEditor).getElement());
    expect(
      internalListener.find('[data-testid="listener-protocol"] option').map((option) => option.prop('value')),
    ).toEqual(['HTTP', 'HTTPS']);
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
    const validate = (editorModule as any).validateGceHttpLoadBalancerCommand || (() => []);

    expect(validate(invalid)).toEqual(
      jasmine.arrayContaining([
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
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceHttpLoadBalancerEditor command={command} data={data} onChange={onChange} />);

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Add host rule')
      .simulate('click');

    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({
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
    const stableWrapper = shallow(<GceHttpLoadBalancerEditor command={withMatcher} data={data} onChange={onChange} />);

    stableWrapper
      .find('[data-testid="default-backend-service"]')
      .simulate('change', { target: { value: 'backend-b' } });

    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({
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
    expect(options.certificates).toContain(
      jasmine.objectContaining({ name: 'removed-cert', selfLink: 'https://compute/sslCertificates/removed-cert' }),
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
    const onChange = jasmine.createSpy('onChange');
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
    const editWrapper = shallow(<GceHttpLoadBalancerEditor command={internal} data={emptyData} onChange={onChange} />);

    ['load-balancer-name', 'load-balancer-type', 'credentials', 'region'].forEach((testId) =>
      expect(editWrapper.find(`[data-testid="${testId}"]`).prop('disabled')).toBe(true),
    );

    const createWrapper = shallow(
      <GceHttpLoadBalancerEditor command={{ ...internal, mode: 'create' }} data={emptyData} onChange={onChange} />,
    );
    ['load-balancer-name', 'load-balancer-type', 'credentials', 'region'].forEach((testId) =>
      expect(createWrapper.find(`[data-testid="${testId}"]`).prop('disabled')).not.toBe(true),
    );
  });

  it('restores account, region, network, subnet, and composite type controls', () => {
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(
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

    expect(wrapper.find('[data-testid="load-balancer-type"]').prop('value')).toBe('INTERNAL_MANAGED');
    expect(wrapper.find('[data-testid="load-balancer-name"]').prop('value')).toBe('web');
    expect(wrapper.find('[data-testid="credentials"]').prop('value')).toBe('account-a');
    expect(wrapper.find('[data-testid="region"]').prop('value')).toBe('europe-west1');
    expect(wrapper.find('[data-testid="network"]').prop('value')).toBe('network-a');
    expect(wrapper.find('[data-testid="subnet"]').prop('value')).toBe('subnet-a');

    wrapper.find('[data-testid="load-balancer-name"]').simulate('change', { target: { value: 'web-updated' } });
    expect(onChange).toHaveBeenCalledWith(jasmine.objectContaining({ name: 'web-updated' }));

    wrapper.find('[data-testid="load-balancer-type"]').simulate('change', { target: { value: 'HTTP' } });

    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({ loadBalancerType: 'HTTP', network: undefined, region: 'global', subnet: undefined }),
    );
  });
});
