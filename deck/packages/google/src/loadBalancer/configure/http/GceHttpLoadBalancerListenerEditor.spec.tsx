import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import { GceHttpLoadBalancerListenerEditor } from './GceHttpLoadBalancerListenerEditor';

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
});

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}
