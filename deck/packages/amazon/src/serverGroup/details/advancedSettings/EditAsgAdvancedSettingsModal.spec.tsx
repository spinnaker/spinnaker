import { mount, shallow } from 'enzyme';
import React from 'react';

import { DeckRuntimeContext, FormikFormField, ReactModal, TaskMonitorModal } from '@spinnaker/core';

import { EditAsgAdvancedSettingsModal } from './EditAsgAdvancedSettingsModal';

describe('EditAsgAdvancedSettingsModal', () => {
  const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1' } as any;
  let buildUpdateServerGroupCommand: jasmine.Spy;
  const modalProps = {
    application,
    serverGroup,
    closeModal: jasmine.createSpy('closeModal'),
    dismissModal: jasmine.createSpy('dismissModal'),
  };

  function mountModal(command: any) {
    buildUpdateServerGroupCommand = jasmine.createSpy().and.returnValue(command);
    const commandBuilder = { buildUpdateServerGroupCommand };
    const runtime = {
      services: { providerServiceDelegate: { getDelegate: jasmine.createSpy().and.returnValue(commandBuilder) } },
    } as any;
    return mount(
      <DeckRuntimeContext.Provider value={runtime}>
        <EditAsgAdvancedSettingsModal {...modalProps} />
      </DeckRuntimeContext.Provider>,
    ).find(EditAsgAdvancedSettingsModal);
  }

  it('builds one update command and preserves it across rerenders', () => {
    const command = {
      backingData: { enabledMetrics: [], healthCheckTypes: [], terminationPolicies: [] },
    } as any;
    const wrapper = mountModal(command);
    const initialValues = wrapper.find(TaskMonitorModal).prop('initialValues');

    wrapper.instance().forceUpdate();
    wrapper.update();

    expect(buildUpdateServerGroupCommand).toHaveBeenCalledTimes(1);
    expect(wrapper.find(TaskMonitorModal).prop('initialValues')).toBe(initialValues);
  });

  it('uses the update command builder and exposes every advanced setting', () => {
    const command = {
      cooldown: 10,
      enabledMetrics: ['GroupDesiredCapacity'],
      healthCheckGracePeriod: 600,
      healthCheckType: 'EC2',
      terminationPolicies: ['Default'],
      capacityRebalance: false,
      backingData: {
        enabledMetrics: ['GroupDesiredCapacity'],
        healthCheckTypes: ['EC2', 'ELB'],
        terminationPolicies: ['Default'],
      },
    } as any;
    const wrapper = mountModal(command);
    const taskModal = wrapper.find(TaskMonitorModal);

    expect(taskModal.prop('initialValues')).toBe(command);
    expect(taskModal.prop('mapValuesToTask')(command)).toEqual({ application, job: [command] });

    const fields = shallow(<div>{taskModal.prop('render')({ values: command } as any)}</div>).find(FormikFormField);
    expect(fields.map((field) => field.prop('name'))).toEqual([
      'cooldown',
      'enabledMetrics',
      'healthCheckType',
      'healthCheckGracePeriod',
      'terminationPolicies',
      'capacityRebalance',
    ]);
  });

  it('requires finite non-negative cooldown and health check grace period values', () => {
    const command = {
      cooldown: 10,
      healthCheckGracePeriod: 600,
      backingData: { enabledMetrics: [], healthCheckTypes: [], terminationPolicies: [] },
    } as any;
    const wrapper = mountModal(command);
    const taskModal = wrapper.find(TaskMonitorModal);
    const fields = shallow(<div>{taskModal.prop('render')({ values: command } as any)}</div>).find(FormikFormField);

    ['cooldown', 'healthCheckGracePeriod'].forEach((name) => {
      const field = fields.filterWhere((candidate) => candidate.prop('name') === name);
      const validate = field.prop('validate') as (value: unknown) => string | undefined;

      expect(field.prop('required')).toBe(true);
      [undefined, null, '', NaN, Infinity, -Infinity, -1].forEach((value) => expect(validate(value)).toBeDefined());
      [0, 0.5, 1].forEach((value) => expect(validate(value)).toBeUndefined());
    });
  });

  it('opens only after managed-resource verification succeeds', async () => {
    const show = spyOn(ReactModal, 'show').and.returnValue(Promise.resolve() as any);
    const runtimeServices = {} as any;

    await EditAsgAdvancedSettingsModal.show({ application, serverGroup }, runtimeServices);

    expect(show).toHaveBeenCalledWith(
      EditAsgAdvancedSettingsModal,
      { application, serverGroup },
      { dialogClassName: 'modal-lg' },
      runtimeServices,
    );
  });
});
