import { mount as enzymeMount } from 'enzyme';
import React from 'react';

import { DeckRuntimeContext } from '@spinnaker/core';
import { CustomInstanceConfigurer } from './CustomInstanceConfigurer';
import { GceCustomInstanceBuilder } from './GceCustomInstanceBuilder';

describe('GceCustomInstanceBuilder', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
  });

  it('parses the current instance type and provides valid custom instance choices', () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = commandWithCustomInstance('n2-custom-4-16384-ext');

    const component = mount(<GceCustomInstanceBuilder command={command as any} onTypeChanged={jasmine.createSpy()} />);
    const configurer = component.find(CustomInstanceConfigurer);

    expect(configurer.prop('selectedInstanceFamily')).toBe('N2');
    expect(configurer.prop('selectedVCpuCount')).toBe(4);
    expect(configurer.prop('selectedMemory')).toBe(16);
    expect(configurer.prop('selectedExtendedMemory')).toBe(true);
    expect(configurer.prop('instanceFamilyList')).toContain('N2D');
    expect(configurer.prop('vCpuList')).toContain(4);
    expect(configurer.prop('memoryList')).toContain(16);
  });

  it('updates command.instanceType, notifies, and loads details when custom choices change', async () => {
    const instanceTypeDetails = { name: 'n2-custom-8-32768' };
    runtimeServices.instanceTypeService = instanceTypeService(instanceTypeDetails);
    const onTypeChanged = jasmine.createSpy('onTypeChanged');
    const command = commandWithCustomInstance('n2-custom-4-16384');

    const component = mount(<GceCustomInstanceBuilder command={command as any} onTypeChanged={onTypeChanged} />);
    component.find(CustomInstanceConfigurer).prop('onChange')({
      instanceFamily: 'N2',
      vCpuCount: 8,
      memory: 32,
      extendedMemory: false,
    });
    await settle(component);

    expect(command.instanceType).toBe('n2-custom-8-32768');
    expect(command.customInstanceChanged).toHaveBeenCalledWith(command);
    expect(onTypeChanged).toHaveBeenCalledWith('n2-custom-8-32768');
    expect(command.viewState.instanceTypeDetails).toBe(instanceTypeDetails as any);
  });

  it('initializes missing custom values from valid lists', () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = commandWithCustomInstance(null);

    const component = mount(<GceCustomInstanceBuilder command={command as any} onTypeChanged={jasmine.createSpy()} />);
    const configurer = component.find(CustomInstanceConfigurer);

    expect(configurer.prop('selectedInstanceFamily')).toBe('N1');
    expect(configurer.prop('selectedVCpuCount')).toBe(1);
    expect(configurer.prop('selectedMemory')).toBe(1);
    expect(command.viewState.customInstance).toEqual({
      extendedMemory: false,
      instanceFamily: 'N1',
      memory: 1,
      vCpuCount: 1,
    });
  });

  it('keeps valid defaults when memory changes before cores', async () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const onTypeChanged = jasmine.createSpy('onTypeChanged');
    const command = commandWithCustomInstance(null);

    const component = mount(<GceCustomInstanceBuilder command={command as any} onTypeChanged={onTypeChanged} />);
    component.find(CustomInstanceConfigurer).prop('onChange')({
      instanceFamily: 'N1',
      vCpuCount: null,
      memory: 4,
      extendedMemory: false,
    } as any);
    await settle(component);

    expect(command.instanceType).toBe('custom-1-4096');
    expect(onTypeChanged).toHaveBeenCalledWith('custom-1-4096');
  });
});

function commandWithCustomInstance(instanceType: string | null) {
  return {
    selectedProvider: 'gce',
    instanceType,
    region: 'us-central1',
    regional: true,
    viewState: {},
    backingData: {
      customInstanceTypes: {},
      credentialsKeyedByAccount: {
        test: {
          locationToInstanceTypesMap: {
            'us-central1': { vCpuMax: 16 },
          },
        },
      },
    },
    credentials: 'test',
    customInstanceChanged: jasmine.createSpy('customInstanceChanged'),
  };
}

function instanceTypeService(details = { name: 'n2-custom-8-32768' }) {
  return {
    getInstanceTypeDetails: jasmine.createSpy('getInstanceTypeDetails').and.returnValue(Promise.resolve(details)),
  };
}

async function settle(component: any) {
  await Promise.resolve();
  await Promise.resolve();
  component.update();
}
