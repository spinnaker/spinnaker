import { shallow } from 'enzyme';
import React from 'react';

import {
  PlatformHealthOverride,
  ReactModal,
  SpinFormik,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
} from '@spinnaker/core';

import { EcsRollbackServerGroupModal, getEcsRollbackTargets, validateEcsRollbackValues } from './index';

describe('EcsRollbackServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    capacity: { desired: 4, max: 8, min: 2 },
    cluster: 'fnord-main',
    moniker: { app: 'fnord', cluster: 'fnord-main' },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
  };

  function application(attributes: any = {}) {
    return {
      attributes,
      name: 'fnord',
      serverGroups: {
        data: [
          target('fnord-main-v003'),
          target('fnord-main-v002', { isDisabled: false }),
          target('other-main-v003', { cluster: 'other-main', moniker: { app: 'fnord', cluster: 'other-main' } }),
          target('fnord-main-v003-other-account', { account: 'other-account' }),
          target('fnord-main-v003-other-region', { region: 'us-east-1' }),
          target('fnord-main-v003-other-app', { moniker: { app: 'other', cluster: 'fnord-main' } }),
        ],
        refresh: jasmine.createSpy('refresh'),
      },
    } as any;
  }

  function target(name: string, overrides: any = {}) {
    return {
      account: 'test-account',
      cluster: 'fnord-main',
      isDisabled: true,
      moniker: { app: 'fnord', cluster: 'fnord-main' },
      name,
      region: 'eu-west-1',
      ...overrides,
    };
  }

  function props(app = application()) {
    return {
      application: app,
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      serverGroup: serverGroup as any,
    };
  }

  it('finds only disabled rollback targets in the same application, cluster, account, and region', () => {
    expect(getEcsRollbackTargets(application(), serverGroup as any).map((candidate) => candidate.name)).toEqual([
      'fnord-main-v003',
    ]);
  });

  it('requires a target and a health threshold from 0 through 100 while keeping reason optional', () => {
    expect(validateEcsRollbackValues({ restoreServerGroupName: '', targetHealthyRollbackPercentage: 100 })).toEqual({
      restoreServerGroupName: 'Select a server group to restore',
    });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: -1 }),
    ).toEqual({ targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100' });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: 101 }),
    ).toEqual({ targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100' });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: 95 }),
    ).toEqual({});
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v999', targetHealthyRollbackPercentage: 95 }, [
        'fnord-main-v003',
      ]),
    ).toEqual({ restoreServerGroupName: 'Select an eligible server group to restore' });
  });

  it('does not submit a rollback target outside the eligible disabled set', () => {
    const app = application();
    const writer = { rollbackServerGroup: jasmine.createSpy('rollbackServerGroup') };
    const component = new EcsRollbackServerGroupModal(props(app));
    (component as any).context = { services: { serverGroupWriter: writer } };
    component.state.verified = true;
    spyOn(component.state.taskMonitor, 'submit');

    (component as any).submit({
      restoreServerGroupName: 'fnord-main-v999',
      targetHealthyRollbackPercentage: 95,
    });

    expect(component.state.taskMonitor.submit).not.toHaveBeenCalled();
    expect(writer.rollbackServerGroup).not.toHaveBeenCalled();
  });

  it('submits the exact shared rollback writer contract through its task monitor', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const writer = { rollbackServerGroup: jasmine.createSpy('rollbackServerGroup').and.returnValue(Promise.resolve()) };
    const component = new EcsRollbackServerGroupModal(props(app));
    (component as any).context = { services: { serverGroupWriter: writer } };
    component.setState = ((update: any) => {
      component.state = { ...component.state, ...(typeof update === 'function' ? update(component.state) : update) };
    }) as any;
    component.setState({ verified: true });
    spyOn(component.state.taskMonitor, 'submit').and.callFake((submitMethod: any) => submitMethod());

    (component as any).submit({
      interestingHealthProviderNames: ['Ecs'],
      reason: '  preserve this reason exactly  ',
      restoreServerGroupName: 'fnord-main-v003',
      targetHealthyRollbackPercentage: 95,
    });

    expect(component.state.taskMonitor.submit).toHaveBeenCalled();
    expect(writer.rollbackServerGroup).toHaveBeenCalledOnceWith(serverGroup, app, {
      interestingHealthProviderNames: ['Ecs'],
      platformHealthOnlyShowOverride: true,
      reason: '  preserve this reason exactly  ',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 95,
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('renders task, verification, reason, threshold, and ECS platform-health controls', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const wrapper = shallow(<EcsRollbackServerGroupModal {...props(app)} />);
    const formik = wrapper.find(SpinFormik);
    expect(wrapper.find(TaskMonitorWrapper).prop('monitor')).toBe((wrapper.instance() as any).state.taskMonitor);

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

    expect(content.find(UserVerification).prop('account')).toBe('test-account');
    expect(content.find(TaskReason).exists()).toBe(true);
    expect(content.find('input[name="targetHealthyRollbackPercentage"]').exists()).toBe(true);
    expect(content.find(PlatformHealthOverride).props()).toEqual(
      jasmine.objectContaining({ interestingHealthProviderNames: ['Ecs'], platformHealthType: 'Ecs' }),
    );
  });

  it('exports a show primitive for later actions integration', () => {
    const show = spyOn(ReactModal, 'show').and.returnValue(Promise.resolve() as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    EcsRollbackServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledOnceWith(EcsRollbackServerGroupModal, modalProps, undefined, runtimeServices);
  });
});
