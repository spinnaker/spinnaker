import { render, screen } from '@testing-library/react';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { TargetGroupDetails } from './TargetGroupDetailsWrapper';

describe('TargetGroupDetails', () => {
  const app = {} as Application;
  const targetGroup = {
    accountId: 'test',
    loadBalancerName: 'lb-1',
    name: 'tg-1',
    provider: 'aws',
    region: 'us-east-1',
    vpcId: 'vpc-1',
  };
  const props = {
    accountId: targetGroup.accountId,
    app,
    name: targetGroup.name,
    provider: targetGroup.provider,
    targetGroup,
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React target group details when configured', () => {
    const ReactTargetGroupDetails = (renderedProps: any) => {
      const exactProps =
        renderedProps.accountId === props.accountId &&
        renderedProps.app === app &&
        renderedProps.name === props.name &&
        renderedProps.provider === props.provider &&
        renderedProps.targetGroup === targetGroup;
      return <div>{exactProps ? renderedProps.targetGroup.name : 'wrong props'}</div>;
    };
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((_provider: string, key: string) =>
        key === 'loadBalancer.targetGroupDetails' ? ReactTargetGroupDetails : null,
      );

    render(<TargetGroupDetails {...props} />);

    expect(screen.getByText('tg-1')).toBeInTheDocument();
    expect(getValue.mock.calls).toEqual([['aws', 'loadBalancer.targetGroupDetails']]);
  });

  it('renders nothing when provider target group details config is missing', () => {
    const getValue = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(null);

    const { container } = render(<TargetGroupDetails {...props} />);

    expect(container).toBeEmptyDOMElement();
    expect(getValue.mock.calls).toEqual([['aws', 'loadBalancer.targetGroupDetails']]);
  });
});
