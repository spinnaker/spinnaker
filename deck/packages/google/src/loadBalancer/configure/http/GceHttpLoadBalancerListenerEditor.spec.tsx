import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import { GceHttpLoadBalancerListenerEditor } from './GceHttpLoadBalancerListenerEditor';
import { buildGceLoadBalancerJobs } from '../common';

describe('GceHttpLoadBalancerListenerEditor', () => {
  it('edits listener addresses and certificates without submitting the parent form', () => {
    const onChange = vi.fn();
    const { getByTestId, getAllByRole } = render(
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
        onRemove={vi.fn()}
        subnets={[]}
      />,
    );

    expect(optionValues(getByTestId('listener-address'))).toContain('removed-address');
    expect(optionValues(getByTestId('listener-certificate'))).toContain('removed-cert');
    expect(getAllByRole('button').every((button) => button.getAttribute('type') === 'button')).toBe(true);

    fireEvent.change(getByTestId('listener-address'), { target: { value: '' } });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ address: undefined, certificate: expect.any(Object), name: 'frontend' }),
    );
  });

  it('supports HTTPS certificates for INTERNAL_MANAGED listeners', () => {
    const { getByTestId } = render(
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
        onChange={vi.fn()}
        onRemove={vi.fn()}
        subnets={[{ name: 'subnet-a' }]}
      />,
    );

    expect(optionValues(getByTestId('listener-protocol'))).toEqual(['HTTP', 'HTTPS']);
    expect(getByTestId('listener-certificate')).toHaveValue('regional-cert');
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`sets and locks port 443 for ${loadBalancerType} HTTPS listeners`, () => {
      const onChange = vi.fn();
      const props = {
        addresses: [],
        certificates: [{ name: 'cert-a' }],
        listener: { name: 'frontend', portRange: '80', protocol: 'HTTP' as const },
        loadBalancerType,
        onChange,
        onRemove: vi.fn(),
        subnets: [],
      };
      const { getByTestId, rerender } = render(<GceHttpLoadBalancerListenerEditor {...props} />);

      fireEvent.change(getByTestId('listener-protocol'), { target: { value: 'HTTPS' } });

      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'frontend', portRange: '443', protocol: 'HTTPS' }),
      );

      rerender(
        <GceHttpLoadBalancerListenerEditor
          {...props}
          listener={{ certificate: { name: 'cert-a' }, name: 'frontend', portRange: '443', protocol: 'HTTPS' }}
        />,
      );
      expect(getByTestId('listener-port')).toBeDisabled();
    });
  });

  it('edits EXTERNAL_MANAGED listener addresses, certificates, and network tier', () => {
    const onChange = vi.fn();
    const { getByLabelText, getByTestId } = render(
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
        onRemove={vi.fn()}
        subnets={[]}
      />,
    );

    expect(optionValues(getByTestId('listener-address'))).toContain('external-address');
    expect(optionValues(getByTestId('listener-certificate'))).toContain('regional-cert');
    const networkTier = getByLabelText('Network tier');
    expect(networkTier).toHaveValue('STANDARD');

    fireEvent.change(networkTier, { target: { value: 'PREMIUM' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ networkTier: 'PREMIUM' }));
  });

  it('does not expose certificate map controls for EXTERNAL_MANAGED listeners', () => {
    const { queryByLabelText, queryByTestId } = render(
      <GceHttpLoadBalancerListenerEditor
        addresses={[]}
        certificates={[{ name: 'regional-cert' }]}
        listener={{ name: 'frontend', portRange: '443', protocol: 'HTTPS' }}
        loadBalancerType="EXTERNAL_MANAGED"
        onChange={vi.fn()}
        onRemove={vi.fn()}
        subnets={[]}
      />,
    );

    expect(queryByTestId('listener-certificate-map')).not.toBeInTheDocument();
    expect(queryByLabelText('Certificate map')).not.toBeInTheDocument();
  });

  it('accepts a direct Certificate Manager URL while preserving selectable Compute certificates', () => {
    const onChange = vi.fn();
    const certificateUrl =
      '//certificatemanager.googleapis.com/projects/test/locations/europe-west1/certificates/manager-cert';
    const props = {
      addresses: [],
      certificates: [{ name: 'compute-cert', selfLink: 'https://compute/sslCertificates/compute-cert' }],
      listener: { name: 'external-https', portRange: '443', protocol: 'HTTPS' as const },
      loadBalancerType: 'EXTERNAL_MANAGED' as const,
      onChange,
      onRemove: vi.fn(),
      subnets: [],
    };
    const { getByLabelText, getByTestId, rerender } = render(<GceHttpLoadBalancerListenerEditor {...props} />);

    expect(optionValues(getByTestId('listener-certificate'))).toContain('compute-cert');
    fireEvent.change(getByTestId('listener-certificate'), { target: { value: 'compute-cert' } });
    const computeListener = onChange.mock.lastCall[0];
    expect(computeListener.certificate).toEqual({
      name: 'compute-cert',
      selfLink: 'https://compute/sslCertificates/compute-cert',
    });
    rerender(<GceHttpLoadBalancerListenerEditor {...props} listener={computeListener} />);
    expect(getByTestId('listener-certificate-manager-url')).toHaveValue('');

    const certificateUrlInput = getByLabelText('Certificate Manager resource URL');
    expect(certificateUrlInput).toBe(getByTestId('listener-certificate-manager-url'));
    fireEvent.change(certificateUrlInput, { target: { value: '//certificate' } });
    rerender(<GceHttpLoadBalancerListenerEditor {...props} listener={onChange.mock.lastCall[0]} />);
    expect(getByTestId('listener-certificate-manager-url')).toHaveValue('//certificate');

    fireEvent.change(getByTestId('listener-certificate-manager-url'), { target: { value: certificateUrl } });

    const listener = onChange.mock.lastCall[0];
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
      const onChange = vi.fn();
      const { getByTestId } = render(
        <GceHttpLoadBalancerListenerEditor
          addresses={[{ address: '203.0.113.10', name: 'external-address', networkTier: 'STANDARD' }]}
          certificates={[]}
          listener={{ name: 'frontend', portRange: '80', protocol: 'HTTP' }}
          loadBalancerType={loadBalancerType}
          onChange={onChange}
          onRemove={vi.fn()}
          subnets={[]}
        />,
      );

      fireEvent.change(getByTestId('listener-address'), { target: { value: 'external-address' } });
      expect(onChange.mock.lastCall[0].networkTier).toBeUndefined();
    });
  });
});

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}
