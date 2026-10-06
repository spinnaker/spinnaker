import { render, screen } from '@testing-library/react';
import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
import { StandaloneSecurityGroupDetails } from './StandaloneSecurityGroupDetails';

describe('StandaloneSecurityGroupDetails', () => {
  const app = { isStandalone: true } as Application;
  const resolvedSecurityGroup = {
    accountId: 'test',
    name: 'sg-123',
    provider: 'kubernetes',
    region: 'us-east-1',
    vpcId: null,
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React security group details when configured', () => {
    const ReactSecurityGroupDetails = ({ app: renderedApp, resolvedSecurityGroup: renderedSecurityGroup }: any) => (
      <div>
        {renderedApp === app && renderedSecurityGroup === resolvedSecurityGroup
          ? renderedSecurityGroup.name
          : 'wrong props'}
      </div>
    );
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((_provider: string, key: string) =>
        key === 'securityGroup.details' ? ReactSecurityGroupDetails : null,
      );

    render(<StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />);

    expect(screen.getByText('sg-123')).toBeInTheDocument();
    expect(getValue.mock.calls).toEqual([['kubernetes', 'securityGroup.details']]);
  });

  it('renders nothing when provider security group details config is missing', () => {
    const getValue = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(null);

    const { container } = render(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );

    expect(container).toBeEmptyDOMElement();
    expect(getValue.mock.calls).toEqual([['kubernetes', 'securityGroup.details']]);
  });
});
