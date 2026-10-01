import React from 'react';
import { shallow } from 'enzyme';

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
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );

    expect(wrapper.text()).toContain('CPU Usage');
    expect(wrapper.text()).toContain('0%');
    expect(wrapper.text()).toContain('Max Scaled-in Replicas');
    expect(wrapper.text()).toContain('overnight (disabled)');
  });

  it('opens edit after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    wrapper.find('[data-testid="edit-autoscaling-policy"]').simulate('click');
    expect(show).not.toHaveBeenCalled();
    await Promise.resolve();
    await Promise.resolve();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup, policy });
  });

  it('does not open edit when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    wrapper.find('[data-testid="edit-autoscaling-policy"]').simulate('click');
    await Promise.resolve();
    await Promise.resolve();

    expect(show).not.toHaveBeenCalled();
  });

  it('opens add after managed-resource confirmation proceeds', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    wrapper.find('[data-testid="add-autoscaling-policy"]').simulate('click');
    expect(show).not.toHaveBeenCalled();
    await Promise.resolve();
    await Promise.resolve();

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    expect(show).toHaveBeenCalledWith({ application, serverGroup: managedServerGroup });
  });

  it('does not open add when managed-resource confirmation is cancelled', async () => {
    const show = vi.spyOn(GceUpsertAutoscalingPolicyModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.reject() as any);
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={undefined as any}
      />,
    );

    wrapper.find('[data-testid="add-autoscaling-policy"]').simulate('click');
    await Promise.resolve();
    await Promise.resolve();

    expect(show).not.toHaveBeenCalled();
  });

  it('offers delete confirmation after managed-resource confirmation proceeds', async () => {
    const confirm = vi
      .spyOn(ConfirmationModalService, 'confirm')
      .mockReturnValueOnce(Promise.resolve() as any)
      .mockReturnValueOnce(Promise.resolve() as any);
    const deletePolicy = vi.spyOn(GceAutoscalingPolicyWriter, 'deleteAutoscalingPolicy').mockReturnValue(undefined);
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    wrapper.find('[data-testid="delete-autoscaling-policy"]').simulate('click');
    await Promise.resolve();
    await Promise.resolve();

    expect(confirm.mock.calls.length).toBe(2);
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
    const wrapper = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={true}
        serverGroup={managedServerGroup}
        policy={policy}
      />,
    );

    wrapper.find('[data-testid="delete-autoscaling-policy"]').simulate('click');
    await Promise.resolve();
    await Promise.resolve();

    expect(confirm.mock.calls.length).toBe(1);
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Pause Management?' }));
    expect(deletePolicy).not.toHaveBeenCalled();
  });

  it('hides all mutation actions when mutations are disabled', () => {
    const details = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={policy}
      />,
    );
    const emptyDetails = shallow(
      <GceAutoscalingPolicyDetails
        application={application}
        mutationsEnabled={false}
        serverGroup={serverGroup}
        policy={undefined as any}
      />,
    );

    expect(details.find('[data-testid="edit-autoscaling-policy"]').exists()).toBe(false);
    expect(details.find('[data-testid="delete-autoscaling-policy"]').exists()).toBe(false);
    expect(emptyDetails.isEmptyRender()).toBe(true);
  });
});
