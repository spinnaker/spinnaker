import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountService, ReactModal } from '@spinnaker/core';

import {
  buildGceAutoscalerResizeRequest,
  buildGceFixedResizeJob,
  GceResizeServerGroupModal,
  validateGceResizeValues,
} from './GceResizeServerGroupModal';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  const ReactModule = await import('react');
  return {
    ...actual,
    TaskMonitorWrapper: ({ monitor }: any) =>
      ReactModule.createElement(
        'button',
        { onClick: () => monitor.closeModal(), type: 'button' },
        'Close monitored task',
      ),
  };
});

describe('GceResizeServerGroupModal', () => {
  beforeEach(() => vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(true));

  it('rejects autoscaler minimum capacity greater than maximum capacity', () => {
    const serverGroup = {
      autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 10 },
    } as any;

    expect(validateGceResizeValues(serverGroup, { newMinNumReplicas: 8, newMaxNumReplicas: 4 })).toEqual({
      newMaxNumReplicas: 'Min cannot be larger than Max',
      newMinNumReplicas: 'Min cannot be larger than Max',
    });
  });

  it('builds the fixed-capacity server group writer job with reason and health override', () => {
    const serverGroup = { name: 'fnord-main-v004', region: 'us-central1' } as any;

    expect(
      buildGceFixedResizeJob(serverGroup, {
        interestingHealthProviderNames: ['Google'],
        newSize: 6,
        reason: 'capacity adjustment',
      }),
    ).toEqual({
      capacity: { desired: 6, max: 6, min: 6 },
      interestingHealthProviderNames: ['Google'],
      reason: 'capacity adjustment',
      region: 'us-central1',
      serverGroupName: 'fnord-main-v004',
      targetSize: 6,
    });
  });

  it('projects the autoscaling policy writer request to min and max while preserving optional params', () => {
    expect(
      buildGceAutoscalerResizeRequest({
        interestingHealthProviderNames: ['Google'],
        newMaxNumReplicas: 12,
        newMinNumReplicas: 3,
        reason: 'raise autoscaling ceiling',
      }),
    ).toEqual({
      params: {
        interestingHealthProviderNames: ['Google'],
        reason: 'raise autoscaling ceiling',
      },
      policy: {
        maxNumReplicas: 12,
        minNumReplicas: 3,
      },
    });
  });

  it('rejects negative fixed capacity', () => {
    expect(validateGceResizeValues({} as any, { newSize: -1 })).toEqual({
      newSize: 'Size must be a finite non-negative integer',
    });
  });

  it('rejects non-finite and fractional fixed capacity', () => {
    [Number.NaN, Number.POSITIVE_INFINITY, 1.5].forEach((newSize) => {
      expect(validateGceResizeValues({} as any, { newSize })).toEqual({
        newSize: 'Size must be a finite non-negative integer',
      });
    });
  });

  it('requires a fixed capacity', () => {
    expect(validateGceResizeValues({} as any, {})).toEqual({ newSize: 'Size is required' });
  });

  it('requires both autoscaler bounds', () => {
    const serverGroup = { autoscalingPolicy: {} } as any;

    expect(validateGceResizeValues(serverGroup, {})).toEqual({
      newMaxNumReplicas: 'Max is required',
      newMinNumReplicas: 'Min is required',
    });
  });

  it('rejects a negative autoscaler minimum', () => {
    const serverGroup = { autoscalingPolicy: {} } as any;

    expect(validateGceResizeValues(serverGroup, { newMinNumReplicas: -1, newMaxNumReplicas: 5 })).toEqual({
      newMinNumReplicas: 'Min must be a finite non-negative integer',
    });
  });

  it('rejects a non-finite or fractional autoscaler minimum', () => {
    const serverGroup = { autoscalingPolicy: {} } as any;

    [Number.NaN, Number.POSITIVE_INFINITY, 1.5].forEach((newMinNumReplicas) => {
      expect(validateGceResizeValues(serverGroup, { newMinNumReplicas, newMaxNumReplicas: 5 })).toEqual({
        newMinNumReplicas: 'Min must be a finite non-negative integer',
      });
    });
  });

  it('rejects a negative autoscaler maximum', () => {
    const serverGroup = { autoscalingPolicy: {} } as any;

    expect(validateGceResizeValues(serverGroup, { newMinNumReplicas: 0, newMaxNumReplicas: -1 })).toEqual({
      newMaxNumReplicas: 'Max must be a finite non-negative integer',
    });
  });

  it('rejects a non-finite or fractional autoscaler maximum', () => {
    const serverGroup = { autoscalingPolicy: {} } as any;

    [Number.NaN, Number.POSITIVE_INFINITY, 1.5].forEach((newMaxNumReplicas) => {
      expect(validateGceResizeValues(serverGroup, { newMinNumReplicas: 0, newMaxNumReplicas })).toEqual({
        newMaxNumReplicas: 'Max must be a finite non-negative integer',
      });
    });
  });

  it('renders fixed-capacity mode for a server group without an autoscaler', () => {
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    expect(screen.getByRole('spinbutton', { name: 'Resize to' })).toBeInTheDocument();
    expect(screen.getByText('Sets desired instance count for this server group.')).toBeInTheDocument();
  });

  it('renders min/max mode for a server group with an autoscaler', () => {
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    expect(screen.getByRole('spinbutton', { name: 'Minimum replicas' })).toBeInTheDocument();
    expect(screen.getByRole('spinbutton', { name: 'Maximum replicas' })).toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: 'Resize to' })).not.toBeInTheDocument();
  });

  it('renders reason, account verification, and the Google platform-health override', () => {
    render(
      <GceResizeServerGroupModal
        application={
          {
            attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
            name: 'fnord',
            serverGroups: { refresh: vi.fn() },
          } as any
        }
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    expect(screen.getByRole('textbox', { name: 'Reason' })).toBeInTheDocument();
    expect(screen.getByText(/Type the name of the account/)).toHaveTextContent('prod');
    expect(screen.getByRole('checkbox', { name: 'Consider only Google health' })).toBeChecked();
  });

  it('submits fixed capacity through the server group writer after verification', async () => {
    const application = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: vi.fn() },
    } as any;
    const serverGroup = {
      account: 'prod',
      asg: { desiredCapacity: 4 },
      name: 'fnord-main-v004',
      region: 'us-central1',
    } as any;
    const resizeServerGroup = vi.fn().mockReturnValue(new Promise(() => undefined));
    render(
      <GceResizeServerGroupModal
        application={application}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={serverGroup}
        serverGroupWriter={{ resizeServerGroup }}
      />,
    );

    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Resize to' }), { target: { value: '6' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), { target: { value: 'capacity adjustment' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Confirm account prod' }), { target: { value: 'prod' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(resizeServerGroup).toHaveBeenCalledWith(serverGroup, application, {
      capacity: { desired: 6, max: 6, min: 6 },
      interestingHealthProviderNames: ['Google'],
      reason: 'capacity adjustment',
      region: 'us-central1',
      serverGroupName: 'fnord-main-v004',
      targetSize: 6,
    });
  });

  it('submits autoscaler bounds through the injected policy writer after verification', async () => {
    const application = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: vi.fn() },
    } as any;
    const serverGroup = {
      account: 'prod',
      autoscalingPolicy: { coolDownPeriodSec: 60, maxNumReplicas: 10, minNumReplicas: 2, mode: 'ON' },
      name: 'fnord-main-v004',
      region: 'us-central1',
    } as any;
    const upsertAutoscalingPolicy = vi.fn().mockReturnValue(new Promise(() => undefined));
    const resizeServerGroup = vi.fn();
    render(
      <GceResizeServerGroupModal
        application={application}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy }}
        dismissModal={vi.fn()}
        serverGroup={serverGroup}
        serverGroupWriter={{ resizeServerGroup } as any}
      />,
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Minimum replicas' }), { target: { value: '3' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum replicas' }), { target: { value: '12' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), {
      target: { value: 'raise autoscaling ceiling' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Confirm account prod' }), { target: { value: 'prod' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(resizeServerGroup).not.toHaveBeenCalled();
    expect(upsertAutoscalingPolicy).toHaveBeenCalledWith(
      application,
      serverGroup,
      { maxNumReplicas: 12, minNumReplicas: 3 },
      {
        interestingHealthProviderNames: ['Google'],
        reason: 'raise autoscaling ceiling',
      },
    );
  });

  it('surfaces invalid autoscaler min/max validation', () => {
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Minimum replicas' }), { target: { value: '8' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum replicas' }), { target: { value: '4' } });

    expect(screen.getByText('Min cannot be larger than Max')).toBeInTheDocument();
  });

  it('opens as a standalone React modal', () => {
    const props = {
      application: { name: 'fnord' },
      autoscalingPolicyWriter: { upsertAutoscalingPolicy: vi.fn() },
      serverGroup: { account: 'prod', name: 'fnord-main-v004', region: 'us-central1' },
      serverGroupWriter: { resizeServerGroup: vi.fn() },
    } as any;
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(Promise.resolve({}) as any);

    GceResizeServerGroupModal.show(props);

    expect(show).toHaveBeenCalledWith(GceResizeServerGroupModal, props);
  });

  it('keeps submit disabled when fixed capacity is cleared', () => {
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Resize to' }), { target: { value: '6' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Resize to' }), { target: { value: '' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Confirm account prod' }), { target: { value: 'prod' } });

    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
  });

  it('requires autoscaler bounds after an input is cleared', () => {
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={vi.fn()}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Minimum replicas' }), { target: { value: '3' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum replicas' }), { target: { value: '12' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Minimum replicas' }), { target: { value: '' } });

    expect(screen.getByText('Min is required')).toBeInTheDocument();
  });

  it('dismisses the modal from the task monitor', () => {
    const dismissModal = vi.fn();
    render(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: vi.fn() } as any}
        dismissModal={dismissModal}
        serverGroup={{ account: 'prod', name: 'fnord-main-v004', region: 'us-central1' } as any}
        serverGroupWriter={{ resizeServerGroup: vi.fn() } as any}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Close monitored task' }));

    expect(dismissModal).toHaveBeenCalled();
  });
});
