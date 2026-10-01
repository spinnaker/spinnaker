import { render, screen } from '@testing-library/react';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { StandaloneInstanceDetails } from './StandaloneInstanceDetails';

describe('StandaloneInstanceDetails', () => {
  const app = { isStandalone: true } as Application;
  const instance = {
    account: 'test',
    instanceId: 'i-123',
    noApplication: true,
    provider: 'kubernetes',
    region: 'us-east-1',
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React instance details when configured', () => {
    const ReactInstanceDetails = ({ app: renderedApp, instance: renderedInstance }: any) => (
      <div>
        {renderedApp === app && renderedInstance === instance ? 'standalone app' : 'wrong props'}:{' '}
        {renderedInstance.instanceId}
      </div>
    );
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((_provider: string, key: string) =>
        key === 'instance.details' ? ReactInstanceDetails : null,
      );

    render(<StandaloneInstanceDetails app={app} instance={instance} />);

    expect(screen.getByText('standalone app: i-123')).toBeInTheDocument();
    expect(getValue.mock.calls).toEqual([['kubernetes', 'instance.details']]);
  });

  it('renders nothing when provider instance details config is missing', () => {
    const getValue = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(null);

    const { container } = render(<StandaloneInstanceDetails app={app} instance={instance} />);

    expect(container).toBeEmptyDOMElement();
    expect(getValue.mock.calls).toEqual([['kubernetes', 'instance.details']]);
  });
});
