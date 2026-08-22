import React from 'react';
import { shallow } from 'enzyme';

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
});
