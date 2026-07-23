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

import { EcsResizeServerGroupModal, validateEcsResizeValues } from './index';

describe('EcsResizeServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    capacity: { desired: 4, max: 8, min: 2 },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
  };

  function application(attributes: any = {}) {
    return {
      attributes,
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
  }

  function props(app = application()) {
    return {
      application: app,
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      serverGroup: serverGroup as any,
    };
  }

  it('validates non-negative min, max, and desired capacities in range', () => {
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 8, min: -1 } })).toEqual({
      capacity: { min: 'Min cannot be negative' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 1, min: 2 } })).toEqual({
      capacity: { max: 'Max cannot be smaller than Min', min: 'Min cannot be larger than Max' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 1, max: 8, min: 2 } })).toEqual({
      capacity: { desired: 'Desired cannot be smaller than Min' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 9, max: 8, min: 2 } })).toEqual({
      capacity: { desired: 'Desired cannot be larger than Max' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 8, min: 2 } })).toEqual({});
  });

  it('submits the exact shared resize writer contract through its task monitor', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const writer = { resizeServerGroup: jasmine.createSpy('resizeServerGroup').and.returnValue(Promise.resolve()) };
    const component = new EcsResizeServerGroupModal(props(app));
    (component as any).context = { services: { serverGroupWriter: writer } };
    component.setState = ((update: any) => {
      component.state = { ...component.state, ...(typeof update === 'function' ? update(component.state) : update) };
    }) as any;
    component.setState({ verified: true });
    spyOn(component.state.taskMonitor, 'submit').and.callFake((submitMethod: any) => submitMethod());

    (component as any).submit({
      capacity: { desired: 6, max: 10, min: 3 },
      interestingHealthProviderNames: ['Ecs'],
      reason: '  preserve this resize reason exactly  ',
    });

    expect(component.state.taskMonitor.submit).toHaveBeenCalled();
    expect(writer.resizeServerGroup).toHaveBeenCalledOnceWith(serverGroup, app, {
      capacity: { desired: 6, max: 10, min: 3 },
      interestingHealthProviderNames: ['Ecs'],
      reason: '  preserve this resize reason exactly  ',
    });
  });

  it('does not submit invalid or unverified resize commands', () => {
    const writer = { resizeServerGroup: jasmine.createSpy('resizeServerGroup') };
    const component = new EcsResizeServerGroupModal(props());
    (component as any).context = { services: { serverGroupWriter: writer } };
    spyOn(component.state.taskMonitor, 'submit');

    (component as any).submit({ capacity: { desired: 9, max: 8, min: 2 } });
    component.state.verified = true;
    (component as any).submit({ capacity: { desired: 9, max: 8, min: 2 } });

    expect(component.state.taskMonitor.submit).not.toHaveBeenCalled();
    expect(writer.resizeServerGroup).not.toHaveBeenCalled();
  });

  it('renders task, capacity, verification, reason, and ECS platform-health controls', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const wrapper = shallow(<EcsResizeServerGroupModal {...props(app)} />);
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
    expect(content.find('[name="capacity.min"]').exists()).toBe(true);
    expect(content.find('[name="capacity.max"]').exists()).toBe(true);
    expect(content.find('[name="capacity.desired"]').exists()).toBe(true);
    expect(content.find(PlatformHealthOverride).props()).toEqual(
      jasmine.objectContaining({ interestingHealthProviderNames: ['Ecs'], platformHealthType: 'Ecs' }),
    );
  });

  it('exports a show primitive for later actions integration', () => {
    const show = spyOn(ReactModal, 'show').and.returnValue(Promise.resolve() as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    EcsResizeServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledOnceWith(EcsResizeServerGroupModal, modalProps, undefined, runtimeServices);
  });
});
