import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { ConfirmationModalService } from '@spinnaker/core';

import { GceAutoHealingPolicyDetails } from './GceAutoHealingPolicyDetails';
import { GceAutoscalingPolicyWriter } from '../../../autoscalingPolicy';
import { GceUpsertAutoHealingPolicyModal } from './modal/GceUpsertAutoHealingPolicyModal';

describe('GceAutoHealingPolicyDetails', () => {
  const application = { name: 'my-app' } as any;
  const serverGroup = { account: 'my-account', name: 'my-app-main-v001', region: 'us-central1' } as any;
  const managedServerGroup = {
    ...serverGroup,
    isManaged: true,
    managedResourceSummary: {
      id: 'managed-resource-id',
      isPaused: false,
      locations: { account: 'my-account', regions: [] },
    },
  } as any;
  const policy = { healthCheck: 'web', initialDelaySec: 0, maxUnavailable: { fixed: 0 } } as any;

  it('summarizes zero-valued delay without rendering legacy max unavailable data', () => {
    const { container } = render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );

    expect(screen.getByText('web')).toBeInTheDocument();
    expect(screen.getByText('0 seconds')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('Max Unavailable');
    expect(container).not.toHaveTextContent('0 fixed');
  });

  it('opens edit after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoHealingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('edit-auto-healing-policy'));
    expect(show).not.toHaveBeenCalled();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    await waitFor(() => expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup, policy }));
  });

  it('does not open edit when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoHealingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('edit-auto-healing-policy'));
    await waitFor(() => expect(ConfirmationModalService.confirm).toHaveBeenCalled());

    expect(show).not.toHaveBeenCalled();
  });

  it('opens add after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoHealingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    fireEvent.click(screen.getByTestId('add-auto-healing-policy'));
    expect(show).not.toHaveBeenCalled();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    await waitFor(() => expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup }));
  });

  it('does not open add when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoHealingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    fireEvent.click(screen.getByTestId('add-auto-healing-policy'));
    await waitFor(() => expect(ConfirmationModalService.confirm).toHaveBeenCalled());

    expect(show).not.toHaveBeenCalled();
  });

  it('offers delete confirmation after managed-resource confirmation proceeds', async () => {
    const confirm = vi
      .spyOn(ConfirmationModalService, 'confirm')
      .mockReturnValueOnce(Promise.resolve() as any)
      .mockReturnValueOnce(Promise.resolve() as any);
    const deletePolicy = vi.spyOn(GceAutoscalingPolicyWriter, 'deleteAutoHealingPolicy').mockReturnValue(undefined);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('delete-auto-healing-policy'));
    await waitFor(() => expect(confirm.mock.calls.length).toBe(2));

    const deleteConfirmation = confirm.mock.lastCall[0];
    deleteConfirmation.submitMethod();

    expect(deletePolicy).toHaveBeenCalledWith(application, managedServerGroup);
  });

  it('does not offer delete confirmation when managed-resource confirmation is cancelled', async () => {
    const cancelledConfirmation = {
      then: (_onProceed: () => void, onCancel: () => void) => Promise.resolve(onCancel()),
    } as any;
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(cancelledConfirmation);
    const deletePolicy = vi.spyOn(GceAutoscalingPolicyWriter, 'deleteAutoHealingPolicy').mockReturnValue(undefined);
    render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('delete-auto-healing-policy'));
    await waitFor(() => expect(confirm).toHaveBeenCalled());

    expect(confirm.mock.calls.length).toBe(1);
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    expect(deletePolicy).not.toHaveBeenCalled();
  });

  it('hides all mutation actions when mutations are disabled', () => {
    const details = render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );
    expect(details.queryByTestId('edit-auto-healing-policy')).not.toBeInTheDocument();
    expect(details.queryByTestId('delete-auto-healing-policy')).not.toBeInTheDocument();
    details.unmount();
    const emptyDetails = render(
      <GceAutoHealingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={undefined as any}
      />,
    );

    expect(emptyDetails.container).toBeEmptyDOMElement();
  });
});
