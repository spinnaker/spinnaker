import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { ConfirmationModalService } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from '../../../autoscalingPolicy';
import { GceAutoscalingPolicyDetails } from './GceAutoscalingPolicyDetails';
import { GceUpsertAutoscalingPolicyModal } from './modal/GceUpsertAutoscalingPolicyModal';

describe('GceAutoscalingPolicyDetails', () => {
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
  const policy = {
    minNumReplicas: 0,
    maxNumReplicas: 5,
    coolDownPeriodSec: 60,
    mode: 'ON' as const,
    cpuUtilization: { utilizationTarget: 0 },
    scaleInControl: { maxScaledInReplicas: { percent: 0 }, timeWindowSec: 60 },
    scalingSchedules: [{ scheduleName: 'overnight', enabled: false }],
  };

  it('summarizes zero-valued metrics, scale-in controls, and schedules', () => {
    const { container } = render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );

    expect(container).toHaveTextContent('CPU Usage: 0%');
    expect(container).toHaveTextContent('Max Scaled-in Replicas');
    expect(container).toHaveTextContent('overnight (disabled)');
  });

  it('opens edit after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('edit-autoscaling-policy'));
    expect(show).not.toHaveBeenCalled();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    await waitFor(() => expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup, policy }));
  });

  it('does not open edit when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('edit-autoscaling-policy'));
    await waitFor(() => expect(ConfirmationModalService.confirm).toHaveBeenCalled());

    expect(show).not.toHaveBeenCalled();
  });

  it('opens add after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    fireEvent.click(screen.getByTestId('add-autoscaling-policy'));
    expect(show).not.toHaveBeenCalled();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    await waitFor(() => expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup }));
  });

  it('does not open add when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    fireEvent.click(screen.getByTestId('add-autoscaling-policy'));
    await waitFor(() => expect(ConfirmationModalService.confirm).toHaveBeenCalled());

    expect(show).not.toHaveBeenCalled();
  });

  it('offers delete confirmation after managed-resource confirmation proceeds', async () => {
    const confirm = vi
      .spyOn(ConfirmationModalService, 'confirm')
      .mockReturnValueOnce(Promise.resolve() as any)
      .mockReturnValueOnce(Promise.resolve() as any);
    const deletePolicy = vi.spyOn(GceAutoscalingPolicyWriter, 'deleteAutoscalingPolicy').mockReturnValue(undefined);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('delete-autoscaling-policy'));
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
    const deletePolicy = vi.spyOn(GceAutoscalingPolicyWriter, 'deleteAutoscalingPolicy').mockReturnValue(undefined);
    render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    fireEvent.click(screen.getByTestId('delete-autoscaling-policy'));
    await waitFor(() => expect(confirm).toHaveBeenCalled());

    expect(confirm.mock.calls.length).toBe(1);
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    expect(deletePolicy).not.toHaveBeenCalled();
  });

  it('hides all mutation actions when mutations are disabled', () => {
    const details = render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );
    expect(details.queryByTestId('edit-autoscaling-policy')).not.toBeInTheDocument();
    expect(details.queryByTestId('delete-autoscaling-policy')).not.toBeInTheDocument();
    details.unmount();
    const emptyDetails = render(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={undefined as any}
      />,
    );

    expect(emptyDetails.container).toBeEmptyDOMElement();
  });
});
