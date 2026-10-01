import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { CloudProviderRegistry, ConfirmationModalService } from '@spinnaker/core';

import { GceLoadBalancerChoiceModal } from '../configure/choice/GceLoadBalancerChoiceModal';
import { GceLoadBalancerActions } from './gceLoadBalancerDetails';

describe('GceLoadBalancerActions', () => {
  const app = { name: 'fnord' } as any;
  const loadBalancer = {
    account: 'account-a',
    instances: [],
    loadBalancerType: 'INTERNAL_MANAGED',
    name: 'fnord-main',
    region: 'europe-west1',
  } as any;
  const managedLoadBalancer = {
    ...loadBalancer,
    isManaged: true,
    managedResourceSummary: { id: 'resource-a', isPaused: false, locations: { account: 'account-a', regions: [] } },
  };

  it('hides write actions when the Google provider is disabled', () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(true);

    const { container } = render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('opens the current load balancer in edit mode through managed-resource gating', async () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);
    const show = vi.spyOn(GceLoadBalancerChoiceModal, 'show').mockReturnValue(Promise.resolve() as any);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);

    const managed = render(<GceLoadBalancerActions app={app} loadBalancer={managedLoadBalancer} />);
    fireEvent.click(screen.getByText('Edit Load Balancer'));
    await vi.waitFor(() =>
      expect(confirm).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ header: 'Pause Management?' })),
    );
    expect(show).not.toHaveBeenCalled();
    managed.unmount();

    render(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    fireEvent.click(screen.getByText('Edit Load Balancer'));
    await vi.waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({
        app,
        application: app,
        forPipelineConfig: false,
        isNew: false,
        loadBalancer,
        mode: 'edit',
      } as any),
    );
  });

  it('keeps delete behind managed-resource gating and disables it while instances are attached', async () => {
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);

    const editable = render(<GceLoadBalancerActions app={app} loadBalancer={managedLoadBalancer} />);
    fireEvent.click(editable.getByText('Delete Load Balancer'));
    await vi.waitFor(() =>
      expect(confirm).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ header: 'Pause Management?' })),
    );
    editable.unmount();
    confirm.mockClear();

    render(
      <GceLoadBalancerActions app={app} loadBalancer={{ ...loadBalancer, instances: [{ name: 'instance-a' }] }} />,
    );
    const attachedDelete = screen.getByText('Delete Load Balancer');
    expect(attachedDelete.closest('li')).toHaveClass('disabled');
    fireEvent.click(attachedDelete);
    await Promise.resolve();
    expect(confirm).not.toHaveBeenCalled();
  });
});
