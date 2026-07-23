import { mount as enzymeMount } from 'enzyme';
import React from 'react';

import { DeckRuntimeContext } from '@spinnaker/core';

import { SimpleModeSelector } from './SimpleModeSelector';

describe('SimpleModeSelector', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
  });

  it('shows instance type rows when an instance profile tile is clicked', async () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = buildCommand();
    const component = mount(
      <SimpleModeSelector
        command={command as any}
        setUnlimitedCpuCredits={jasmine.createSpy('setUnlimitedCpuCredits')}
        setFieldValue={jasmine.createSpy('setFieldValue')}
        clearWarnings={jasmine.createSpy('clearWarnings')}
      />,
    );
    await settle(component);

    expect(component.find('.instance-type-row').exists()).toBe(false);

    component.find('button.instance-profile').simulate('click');
    await settle(component);

    expect(component.find('.instance-type-row').text()).toContain('m5.large');
  });

  it('updates derived command fields from the selected instance type', async () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = buildCommand();
    const component = mount(
      <SimpleModeSelector
        command={command as any}
        setUnlimitedCpuCredits={jasmine.createSpy('setUnlimitedCpuCredits')}
        setFieldValue={jasmine.createSpy('setFieldValue')}
        clearWarnings={jasmine.createSpy('clearWarnings')}
      />,
    );
    await settle(component);

    component.find('button.instance-profile').simulate('click');
    await settle(component);
    component.find('.instance-type-row').simulate('click');

    expect(command.instanceTypeChanged).toHaveBeenCalledWith(jasmine.objectContaining({ instanceType: 'm5.large' }));
  });
});

function buildCommand() {
  return {
    backingData: { filtered: { instanceTypes: ['m5.large'] } },
    instanceTypeChanged: jasmine.createSpy('instanceTypeChanged'),
    selectedProvider: 'aws',
    viewState: { dirty: {} },
  };
}

function instanceTypeService() {
  return {
    getCategories: jasmine.createSpy('getCategories').and.returnValue(
      Promise.resolve([
        {
          type: 'general',
          label: 'General Purpose',
          icon: 'cloud',
          description: 'General purpose',
          families: [
            {
              type: 'm5',
              description: 'Balanced compute',
              instanceTypes: [
                {
                  name: 'm5.large',
                  label: 'm5.large',
                  cpu: 2,
                  memory: 8,
                  storage: { count: 1, size: 0, type: 'EBS' },
                },
              ],
            },
          ],
        },
      ]),
    ),
    getInstanceTypeDetails: jasmine.createSpy('getInstanceTypeDetails').and.returnValue(Promise.resolve({})),
  };
}

async function settle(component: any) {
  await Promise.resolve();
  await Promise.resolve();
  component.update();
}
