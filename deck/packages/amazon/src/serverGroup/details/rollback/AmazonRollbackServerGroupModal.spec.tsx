import { shallow } from 'enzyme';
import React from 'react';

import {
  PlatformHealthOverride,
  ReactModal,
  ServerGroupWriter,
  SpinFormik,
  TaskExecutor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
} from '@spinnaker/core';

import type { IAmazonServerGroup } from '../../../domain';
import {
  AmazonRollbackServerGroupModal,
  buildAmazonRollbackJob,
  getAmazonPreviousImageServerGroup,
  getAmazonRollbackType,
  getDefaultAmazonHealthyRollbackPercentage,
  validateAmazonRollbackValues,
} from './index';

describe('AmazonRollbackServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    app: 'fnord',
    capacity: { desired: 10, max: 12, min: 2 },
    cluster: 'fnord-main',
    instanceCounts: { total: 10 },
    moniker: { app: 'fnord', cluster: 'fnord-main' },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
    type: 'aws',
  } as IAmazonServerGroup;

  function previousServerGroup(name = 'fnord-main-v003'): IAmazonServerGroup {
    return {
      ...serverGroup,
      isDisabled: true,
      name,
    };
  }

  function application(attributes: any = {}) {
    return {
      attributes,
      name: 'fnord',
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
  }

  function props(app = application(), overrides: any = {}) {
    return {
      allServerGroups: [previousServerGroup()],
      application: app,
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      previousServerGroup: previousServerGroup(),
      serverGroup,
      ...overrides,
    };
  }

  it('uses capacity-sensitive healthy percentage defaults', () => {
    expect(getDefaultAmazonHealthyRollbackPercentage(0)).toBe(100);
    expect(getDefaultAmazonHealthyRollbackPercentage(9)).toBe(100);
    expect(getDefaultAmazonHealthyRollbackPercentage(10)).toBe(90);
    expect(getDefaultAmazonHealthyRollbackPercentage(19)).toBe(90);
    expect(getDefaultAmazonHealthyRollbackPercentage(20)).toBe(95);
  });

  it('normalizes previous-image metadata and selects that mode only without deployed candidates', () => {
    const withPreviousImage = {
      ...serverGroup,
      entityTags: {
        creationMetadata: {
          value: {
            previousServerGroup: {
              buildInfo: { jenkins: { number: 42 } },
              imageId: 'ami-012345',
              imageName: 'fnord-20260712',
              name: 'fnord-main-v003',
            },
          },
        },
      },
    } as any;

    expect(getAmazonPreviousImageServerGroup(withPreviousImage)).toEqual({
      buildNumber: 42,
      imageId: 'ami-012345',
      imageName: 'fnord-20260712',
      name: 'fnord-main-v003',
    });
    expect(getAmazonRollbackType(withPreviousImage, [])).toBe('PREVIOUS_IMAGE');
    expect(getAmazonRollbackType(withPreviousImage, [previousServerGroup()])).toBe('EXPLICIT');
    expect(
      getAmazonPreviousImageServerGroup({
        ...withPreviousImage,
        entityTags: {
          creationMetadata: {
            value: {
              previousServerGroup: {
                imageId: 'fnord-20260712',
                imageName: 'fnord-20260712',
                name: 'fnord-main-v003',
              },
            },
          },
        },
      }),
    ).toEqual({ imageId: undefined, imageName: 'fnord-20260712', name: 'fnord-main-v003' });
  });

  it('requires an eligible explicit target and valid health percentage and delay', () => {
    const valid = {
      delayBeforeDisableSeconds: 0,
      restoreServerGroupName: 'fnord-main-v003',
      targetHealthyRollbackPercentage: 95,
    };

    expect(validateAmazonRollbackValues(valid, 'EXPLICIT', ['fnord-main-v003'])).toEqual({});
    expect(validateAmazonRollbackValues({ ...valid, restoreServerGroupName: undefined }, 'EXPLICIT')).toEqual({
      restoreServerGroupName: 'Select a server group to restore',
    });
    expect(validateAmazonRollbackValues(valid, 'EXPLICIT', ['fnord-main-v002'])).toEqual({
      restoreServerGroupName: 'Select an eligible server group to restore',
    });
    [-1, 101, Number.NaN, Number.POSITIVE_INFINITY].forEach((targetHealthyRollbackPercentage) =>
      expect(validateAmazonRollbackValues({ ...valid, targetHealthyRollbackPercentage }, 'EXPLICIT')).toEqual({
        targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100',
      }),
    );
    [-1, 1.5, Number.NaN, Number.POSITIVE_INFINITY].forEach((delayBeforeDisableSeconds) =>
      expect(validateAmazonRollbackValues({ ...valid, delayBeforeDisableSeconds }, 'EXPLICIT')).toEqual({
        delayBeforeDisableSeconds: 'Delay must be a non-negative whole number',
      }),
    );
    expect(validateAmazonRollbackValues({ ...valid, restoreServerGroupName: undefined }, 'PREVIOUS_IMAGE', [])).toEqual(
      {},
    );
  });

  it('builds the exact rollback writer command', () => {
    expect(
      buildAmazonRollbackJob(application({ platformHealthOnlyShowOverride: true }), serverGroup, 'EXPLICIT', {
        delayBeforeDisableSeconds: 15,
        interestingHealthProviderNames: ['Amazon'],
        reason: '  bad release  ',
        restoreServerGroupName: 'fnord-main-v003',
        targetHealthyRollbackPercentage: 90,
      }),
    ).toEqual({
      interestingHealthProviderNames: ['Amazon'],
      platformHealthOnlyShowOverride: true,
      reason: '  bad release  ',
      rollbackContext: {
        delayBeforeDisableSeconds: 15,
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 90,
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('passes the exact rollback job envelope from the writer to TaskExecutor', () => {
    const app = application();
    const command = buildAmazonRollbackJob(app, serverGroup, 'PREVIOUS_IMAGE', {
      delayBeforeDisableSeconds: 0,
      targetHealthyRollbackPercentage: 90,
    });
    spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);

    new ServerGroupWriter(null).rollbackServerGroup(serverGroup, app, command);

    expect(TaskExecutor.executeTask).toHaveBeenCalledOnceWith({
      application: app,
      description: 'Rollback Server Group: fnord-main-v004',
      job: [
        {
          cloudProvider: 'aws',
          credentials: 'test-account',
          interestingHealthProviderNames: undefined,
          moniker: serverGroup.moniker,
          platformHealthOnlyShowOverride: undefined,
          reason: undefined,
          region: 'eu-west-1',
          rollbackContext: {
            delayBeforeDisableSeconds: 0,
            restoreServerGroupName: undefined,
            rollbackServerGroupName: 'fnord-main-v004',
            targetHealthyRollbackPercentage: 90,
          },
          rollbackType: 'PREVIOUS_IMAGE',
          type: 'rollbackServerGroup',
        },
      ],
    });
  });

  it('renders explicit controls, verification, reason, and Amazon health override', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const wrapper = shallow(<AmazonRollbackServerGroupModal {...props(app)} />);
    const formik = wrapper.find(SpinFormik);
    const content = shallow(
      <div>
        {(formik.prop('render') as any)({
          errors: {},
          isValid: true,
          setFieldValue: jasmine.createSpy('setFieldValue'),
          values: formik.prop('initialValues'),
        })}
      </div>,
    );

    expect(wrapper.find(TaskMonitorWrapper).prop('monitor')).toBe((wrapper.instance() as any).state.taskMonitor);
    expect(content.find('select[name="restoreServerGroupName"]').exists()).toBe(true);
    expect(content.find('input[name="delayBeforeDisableSeconds"]').exists()).toBe(true);
    expect(content.find('input[name="targetHealthyRollbackPercentage"]').exists()).toBe(true);
    expect(content.find(TaskReason).exists()).toBe(true);
    expect(content.find(UserVerification).prop('account')).toBe('test-account');
    expect(content.find(PlatformHealthOverride).props()).toEqual(
      jasmine.objectContaining({ interestingHealthProviderNames: ['Amazon'], platformHealthType: 'Amazon' }),
    );
  });

  it('submits only after verification and preserves the exact values', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const writer = { rollbackServerGroup: jasmine.createSpy('rollbackServerGroup').and.returnValue(Promise.resolve()) };
    const component = new AmazonRollbackServerGroupModal(props(app));
    (component as any).context = { services: { serverGroupWriter: writer } };
    spyOn(component.state.taskMonitor, 'submit').and.callFake((submitMethod: any) => submitMethod());
    const values = {
      delayBeforeDisableSeconds: 30,
      interestingHealthProviderNames: ['Amazon'],
      reason: 'rollback requested',
      restoreServerGroupName: 'fnord-main-v003',
      targetHealthyRollbackPercentage: 95,
    };

    (component as any).submit(values);
    expect(writer.rollbackServerGroup).not.toHaveBeenCalled();

    component.state.verified = true;
    (component as any).submit(values);

    expect(writer.rollbackServerGroup).toHaveBeenCalledOnceWith(serverGroup, app, {
      interestingHealthProviderNames: ['Amazon'],
      platformHealthOnlyShowOverride: true,
      reason: 'rollback requested',
      rollbackContext: {
        delayBeforeDisableSeconds: 30,
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 95,
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('refreshes after task completion and dismisses through the task monitor', () => {
    const app = application();
    const modalProps = props(app);
    const component = new AmazonRollbackServerGroupModal(modalProps);

    component.state.taskMonitor.config.onTaskComplete();
    component.state.taskMonitor.closeModal();

    expect(app.serverGroups.refresh).toHaveBeenCalled();
    expect(modalProps.dismissModal).toHaveBeenCalled();
  });

  it('exposes a show primitive for later actions integration', () => {
    const show = spyOn(ReactModal, 'show').and.returnValue(Promise.resolve() as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    AmazonRollbackServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledOnceWith(AmazonRollbackServerGroupModal, modalProps, undefined, runtimeServices);
  });
});
