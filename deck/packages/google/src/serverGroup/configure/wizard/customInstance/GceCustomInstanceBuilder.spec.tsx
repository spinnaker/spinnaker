import { mount } from 'enzyme';
import React from 'react';

import { AngularServices } from '@spinnaker/core';
import { CustomInstanceConfigurer } from './CustomInstanceConfigurer';
import { GceCustomInstanceBuilder } from './GceCustomInstanceBuilder';

describe('GceCustomInstanceBuilder', () => {
  it('parses the current instance type and provides valid custom instance choices', () => {
    spyOnProperty(AngularServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
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
    spyOnProperty(AngularServices, 'instanceTypeService', 'get').and.returnValue(
      instanceTypeService(instanceTypeDetails) as any,
    );
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
    spyOnProperty(AngularServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
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
    spyOnProperty(AngularServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
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
