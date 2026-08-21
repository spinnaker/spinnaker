import React from 'react';
import { shallow } from 'enzyme';

import { buildGceLoadBalancerJobs } from '../common';

import { GceHttpLoadBalancerListenerEditor } from './GceHttpLoadBalancerListenerEditor';

describe('GceHttpLoadBalancerListenerEditor', () => {
  it('edits listener addresses and certificates without submitting the parent form', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceHttpLoadBalancerListenerEditor
        addresses={[{ name: 'removed-address', selfLink: 'https://compute/addresses/removed-address' }]}
        certificates={[{ name: 'removed-cert', selfLink: 'https://compute/certificates/removed-cert' }]}
        listener={{
          address: { name: 'removed-address', selfLink: 'https://compute/addresses/removed-address' },
          certificate: { name: 'removed-cert', selfLink: 'https://compute/certificates/removed-cert' },
          name: 'frontend',
          portRange: '443',
          protocol: 'HTTPS',
        }}
        loadBalancerType="HTTP"
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
        subnets={[]}
      />,
    );

    expect(wrapper.find('[data-testid="listener-address"] option').map((option) => option.prop('value'))).toContain(
      'removed-address',
    );
    expect(wrapper.find('[data-testid="listener-certificate"] option').map((option) => option.prop('value'))).toContain(
      'removed-cert',
    );
    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);

    wrapper.find('[data-testid="listener-address"]').simulate('change', { target: { value: '' } });
    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({ address: undefined, certificate: jasmine.any(Object), name: 'frontend' }),
    );
  });

  it('supports HTTPS certificates for INTERNAL_MANAGED listeners', () => {
    const wrapper = shallow(
      <GceHttpLoadBalancerListenerEditor
        addresses={[]}
        certificates={[{ name: 'regional-cert' }]}
        listener={{
          certificate: { name: 'regional-cert' },
          name: 'internal-https',
          portRange: '443',
          protocol: 'HTTPS',
          subnet: { name: 'subnet-a' },
        }}
        loadBalancerType="INTERNAL_MANAGED"
        onChange={jasmine.createSpy('onChange')}
        onRemove={jasmine.createSpy('onRemove')}
        subnets={[{ name: 'subnet-a' }]}
      />,
    );

    expect(wrapper.find('[data-testid="listener-protocol"] option').map((option) => option.prop('value'))).toEqual([
      'HTTP',
      'HTTPS',
    ]);
    expect(wrapper.find('[data-testid="listener-certificate"]').prop('value')).toBe('regional-cert');
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`sets and locks port 443 for ${loadBalancerType} HTTPS listeners`, () => {
      const onChange = jasmine.createSpy('onChange');
      const wrapper = shallow(
        <GceHttpLoadBalancerListenerEditor
          addresses={[]}
          certificates={[{ name: 'cert-a' }]}
          listener={{ name: 'frontend', portRange: '80', protocol: 'HTTP' }}
          loadBalancerType={loadBalancerType}
          onChange={onChange}
          onRemove={jasmine.createSpy('onRemove')}
          subnets={[]}
        />,
      );

      wrapper.find('[data-testid="listener-protocol"]').simulate('change', { target: { value: 'HTTPS' } });

      expect(onChange).toHaveBeenCalledWith(
        jasmine.objectContaining({ name: 'frontend', portRange: '443', protocol: 'HTTPS' }),
      );

      wrapper.setProps({
        listener: { certificate: { name: 'cert-a' }, name: 'frontend', portRange: '443', protocol: 'HTTPS' },
      });
      expect(wrapper.find('[data-testid="listener-port"]').prop('disabled')).toBe(true);
    });
  });

  it('edits EXTERNAL_MANAGED listener addresses, certificates, and network tier', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceHttpLoadBalancerListenerEditor
        addresses={[{ address: '203.0.113.10', name: 'external-address' }]}
        certificates={[
          {
            name: 'regional-cert',
            selfLink:
              '//certificatemanager.googleapis.com/projects/test/locations/europe-west1/certificates/regional-cert',
          },
        ]}
        listener={{
          address: { name: 'external-address', address: '203.0.113.10' },
          certificate: {
            name: 'regional-cert',
            selfLink:
              '//certificatemanager.googleapis.com/projects/test/locations/europe-west1/certificates/regional-cert',
          },
          name: 'app-https',
          networkTier: 'STANDARD',
          portRange: '443',
          protocol: 'HTTPS',
        }}
        loadBalancerType="EXTERNAL_MANAGED"
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
        subnets={[]}
      />,
    );

    expect(wrapper.find('[data-testid="listener-address"] option').map((option) => option.prop('value'))).toContain(
      'external-address',
    );
    expect(wrapper.find('[data-testid="listener-certificate"] option').map((option) => option.prop('value'))).toContain(
      'regional-cert',
    );
    expect(wrapper.find('[data-testid="listener-network-tier"]').prop('value')).toBe('STANDARD');

    wrapper.find('[data-testid="listener-network-tier"]').simulate('change', { target: { value: 'PREMIUM' } });
    expect(onChange).toHaveBeenCalledWith(jasmine.objectContaining({ networkTier: 'PREMIUM' }));
  });

  it('does not expose certificate map controls for EXTERNAL_MANAGED listeners', () => {
    const wrapper = shallow(
      <GceHttpLoadBalancerListenerEditor
        addresses={[]}
        certificates={[{ name: 'regional-cert' }]}
        listener={{ name: 'frontend', portRange: '443', protocol: 'HTTPS' }}
        loadBalancerType="EXTERNAL_MANAGED"
        onChange={jasmine.createSpy('onChange')}
        onRemove={jasmine.createSpy('onRemove')}
        subnets={[]}
      />,
    );

    expect(wrapper.find('[data-testid="listener-certificate-map"]').exists()).toBe(false);
  });

  it('accepts a direct Certificate Manager URL while preserving selectable Compute certificates', () => {
    const onChange = jasmine.createSpy('onChange');
    const certificateUrl =
      '//certificatemanager.googleapis.com/projects/test/locations/europe-west1/certificates/manager-cert';
    const wrapper = shallow(
      <GceHttpLoadBalancerListenerEditor
        addresses={[]}
        certificates={[{ name: 'compute-cert', selfLink: 'https://compute/sslCertificates/compute-cert' }]}
        listener={{ name: 'external-https', portRange: '443', protocol: 'HTTPS' }}
        loadBalancerType="EXTERNAL_MANAGED"
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
        subnets={[]}
      />,
    );

    expect(wrapper.find('[data-testid="listener-certificate"] option').map((option) => option.prop('value'))).toContain(
      'compute-cert',
    );
    wrapper.find('[data-testid="listener-certificate"]').simulate('change', { target: { value: 'compute-cert' } });
    const computeListener = onChange.calls.mostRecent().args[0];
    expect(computeListener.certificate).toEqual({
      name: 'compute-cert',
      selfLink: 'https://compute/sslCertificates/compute-cert',
    });
    wrapper.setProps({ listener: computeListener });
    expect(wrapper.find('[data-testid="listener-certificate-manager-url"]').prop('value')).toBe('');

    let certificateUrlInput = wrapper.find('[data-testid="listener-certificate-manager-url"]');
    expect(certificateUrlInput.exists()).toBe(true);
    expect(certificateUrlInput.closest('label').text()).toContain('Certificate Manager resource URL');
    certificateUrlInput.simulate('change', { target: { value: '//certificate' } });
    wrapper.setProps({ listener: onChange.calls.mostRecent().args[0] });
    certificateUrlInput = wrapper.find('[data-testid="listener-certificate-manager-url"]');
    expect(certificateUrlInput.prop('value')).toBe('//certificate');

    certificateUrlInput.simulate('change', { target: { value: certificateUrl } });

    const listener = onChange.calls.mostRecent().args[0];
    expect(listener.certificate).toEqual({ name: 'manager-cert', selfLink: certificateUrl });
    expect(listener.certificateMap).toBeUndefined();

    const operations = buildGceLoadBalancerJobs({
      backendServices: [{ healthCheck: { name: 'check-a' }, name: 'backend-a', portName: 'http' }],
      credentials: 'account-a',
      defaultService: { name: 'backend-a' },
      healthChecks: [{ healthCheckType: 'HTTPS', name: 'check-a', port: 443, requestPath: '/health' }],
      hostRules: [],
      listeners: [listener],
      loadBalancerType: 'EXTERNAL_MANAGED',
      mode: 'pipeline',
      name: 'app-main',
      network: { name: 'default' },
      region: 'europe-west1',
    } as any);
    expect(operations[0].certificate).toBe(certificateUrl);
    expect(operations[0].certificateMap).toBeUndefined();
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`does not propagate address networkTier for ${loadBalancerType} listeners`, () => {
      const onChange = jasmine.createSpy('onChange');
      const wrapper = shallow(
        <GceHttpLoadBalancerListenerEditor
          addresses={[{ address: '203.0.113.10', name: 'external-address', networkTier: 'STANDARD' }]}
          certificates={[]}
          listener={{ name: 'frontend', portRange: '80', protocol: 'HTTP' }}
          loadBalancerType={loadBalancerType}
          onChange={onChange}
          onRemove={jasmine.createSpy('onRemove')}
          subnets={[]}
        />,
      );

      wrapper.find('[data-testid="listener-address"]').simulate('change', { target: { value: 'external-address' } });
      expect(onChange.calls.mostRecent().args[0].networkTier).toBeUndefined();
    });
  });
});
