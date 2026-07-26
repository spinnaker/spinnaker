import { shallow } from 'enzyme';
import React from 'react';

import { PlatformHealthOverride, ReactModal, TaskReason, UserVerification, ValidationMessage } from '@spinnaker/core';

import {
  buildGceAutoscalerResizeRequest,
  buildGceFixedResizeJob,
  GceResizeServerGroupModal,
  validateGceResizeValues,
} from './GceResizeServerGroupModal';

describe('GceResizeServerGroupModal', () => {
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
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    expect(wrapper.find('input[name="newSize"]').exists()).toBe(true);
    expect(wrapper.find('p').text()).toBe('Sets desired instance count for this server group.');
  });

  it('renders min/max mode for a server group with an autoscaler', () => {
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    expect(wrapper.find('input[name="newMinNumReplicas"]').exists()).toBe(true);
    expect(wrapper.find('input[name="newMaxNumReplicas"]').exists()).toBe(true);
    expect(wrapper.find('input[name="newSize"]').exists()).toBe(false);
  });

  it('renders reason, account verification, and the Google platform-health override', () => {
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={
          {
            attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
            name: 'fnord',
            serverGroups: { refresh: jasmine.createSpy('refresh') },
          } as any
        }
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    expect(wrapper.find(TaskReason).exists()).toBe(true);
    expect(wrapper.find(UserVerification).prop('account')).toBe('prod');
    expect(wrapper.find(PlatformHealthOverride).prop('interestingHealthProviderNames')).toEqual(['Google']);
  });

  it('submits fixed capacity through the server group writer after verification', () => {
    const application = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
    const serverGroup = {
      account: 'prod',
      asg: { desiredCapacity: 4 },
      name: 'fnord-main-v004',
      region: 'us-central1',
    } as any;
    const resizeServerGroup = jasmine.createSpy('resizeServerGroup').and.returnValue(Promise.resolve({}));
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={application}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={serverGroup}
        serverGroupWriter={{ resizeServerGroup }}
      />,
    );

    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(true);
    wrapper.find('input[name="newSize"]').simulate('change', { target: { value: '6' } });
    wrapper.find(TaskReason).prop('onChange')('capacity adjustment');
    wrapper.find(UserVerification).prop('onValidChange')(true);
    const monitor = (wrapper.state() as any).taskMonitor;
    spyOn(monitor, 'submit').and.callFake((submitMethod: () => PromiseLike<any>) => submitMethod());
    wrapper.find('button.btn-primary').simulate('click');

    expect(resizeServerGroup).toHaveBeenCalledWith(serverGroup, application, {
      capacity: { desired: 6, max: 6, min: 6 },
      interestingHealthProviderNames: ['Google'],
      reason: 'capacity adjustment',
      region: 'us-central1',
      serverGroupName: 'fnord-main-v004',
      targetSize: 6,
    });
  });

  it('submits autoscaler bounds through the injected policy writer after verification', () => {
    const application = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
    const serverGroup = {
      account: 'prod',
      autoscalingPolicy: { coolDownPeriodSec: 60, maxNumReplicas: 10, minNumReplicas: 2, mode: 'ON' },
      name: 'fnord-main-v004',
      region: 'us-central1',
    } as any;
    const upsertAutoscalingPolicy = jasmine.createSpy('upsertAutoscalingPolicy').and.returnValue(Promise.resolve({}));
    const resizeServerGroup = jasmine.createSpy('resizeServerGroup');
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={application}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy }}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={serverGroup}
        serverGroupWriter={{ resizeServerGroup } as any}
      />,
    );

    wrapper.find('input[name="newMinNumReplicas"]').simulate('change', { target: { value: '3' } });
    wrapper.find('input[name="newMaxNumReplicas"]').simulate('change', { target: { value: '12' } });
    wrapper.find(TaskReason).prop('onChange')('raise autoscaling ceiling');
    wrapper.find(UserVerification).prop('onValidChange')(true);
    const monitor = (wrapper.state() as any).taskMonitor;
    spyOn(monitor, 'submit').and.callFake((submitMethod: () => PromiseLike<any>) => submitMethod());
    wrapper.find('button.btn-primary').simulate('click');

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
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    wrapper.find('input[name="newMinNumReplicas"]').simulate('change', { target: { value: '8' } });
    wrapper.find('input[name="newMaxNumReplicas"]').simulate('change', { target: { value: '4' } });

    expect(wrapper.find(ValidationMessage).prop('message')).toBe('Min cannot be larger than Max');
  });

  it('opens as a standalone React modal', () => {
    const props = {
      application: { name: 'fnord' },
      autoscalingPolicyWriter: { upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') },
      serverGroup: { account: 'prod', name: 'fnord-main-v004', region: 'us-central1' },
      serverGroupWriter: { resizeServerGroup: jasmine.createSpy('resizeServerGroup') },
    } as any;
    const show = spyOn(ReactModal, 'show').and.returnValue(Promise.resolve({}) as any);

    GceResizeServerGroupModal.show(props);

    expect(show).toHaveBeenCalledWith(GceResizeServerGroupModal, props);
  });

  it('keeps submit disabled when fixed capacity is cleared', () => {
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            asg: { desiredCapacity: 4 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    wrapper.find('input[name="newSize"]').simulate('change', { target: { value: '6' } });
    wrapper.find('input[name="newSize"]').simulate('change', { target: { value: '' } });
    wrapper.find(UserVerification).prop('onValidChange')(true);

    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(true);
  });

  it('requires autoscaler bounds after an input is cleared', () => {
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={
          {
            account: 'prod',
            autoscalingPolicy: { maxNumReplicas: 10, minNumReplicas: 2 },
            name: 'fnord-main-v004',
            region: 'us-central1',
          } as any
        }
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    wrapper.find('input[name="newMinNumReplicas"]').simulate('change', { target: { value: '3' } });
    wrapper.find('input[name="newMaxNumReplicas"]').simulate('change', { target: { value: '12' } });
    wrapper.find('input[name="newMinNumReplicas"]').simulate('change', { target: { value: '' } });

    expect(wrapper.find(ValidationMessage).prop('message')).toBe('Min is required');
  });

  it('dismisses the modal from the task monitor', () => {
    const dismissModal = jasmine.createSpy('dismissModal');
    const wrapper = shallow(
      <GceResizeServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        autoscalingPolicyWriter={{ upsertAutoscalingPolicy: jasmine.createSpy('upsertAutoscalingPolicy') } as any}
        dismissModal={dismissModal}
        serverGroup={{ account: 'prod', name: 'fnord-main-v004', region: 'us-central1' } as any}
        serverGroupWriter={{ resizeServerGroup: jasmine.createSpy('resizeServerGroup') } as any}
      />,
    );

    (wrapper.state() as any).taskMonitor.closeModal();

    expect(dismissModal).toHaveBeenCalled();
  });
});
