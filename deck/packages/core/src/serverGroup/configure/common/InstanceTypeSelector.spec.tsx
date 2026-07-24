import { mount as enzymeMount } from 'enzyme';
import React from 'react';

import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { InstanceTypeSelector } from './InstanceTypeSelector';
import { V2InstanceTypeSelectorController, v2InstanceTypeSelector } from './v2InstanceTypeSelector.component';

describe('InstanceTypeSelector', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
    Object.defineProperty(runtimeServices, 'instanceTypeService', { configurable: true, get: () => undefined });
  });

  it('registers the Angular v2 component with a custom change-checking React controller', () => {
    expect(v2InstanceTypeSelector.templateUrl).toBeUndefined();
    expect(v2InstanceTypeSelector.controller).toBe(V2InstanceTypeSelectorController);
    expect(V2InstanceTypeSelectorController.prototype.$doCheck).toBeDefined();
  });

  it('renders without the AngularJS adapter and ignores unavailable instance types', async () => {
    const instanceTypeService = serviceWithCategories();
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService as any);
    const command = commandWithFilteredTypes(['m5.large']);

    const component = mount(<InstanceTypeSelector command={command as any} onTypeChanged={jasmine.createSpy()} />);
    await settle(component);

    expect(component.find(`.Angular${'JS'}Adapter`).exists()).toBe(false);
    component.find('tr.instance-type-row').at(1).simulate('click');

    expect(command.instanceType).toBeUndefined();
    expect(instanceTypeService.getInstanceTypeDetails).not.toHaveBeenCalled();
  });

  it('selects an available instance type, clears dirty state, loads details, and notifies', async () => {
    const instanceTypeDetails = { name: 'm5.large' };
    const instanceTypeService = serviceWithCategories(instanceTypeDetails);
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService as any);
    const onTypeChanged = jasmine.createSpy('onTypeChanged');
    const command = commandWithFilteredTypes(['m5.large']);

    const component = mount(<InstanceTypeSelector command={command as any} onTypeChanged={onTypeChanged} />);
    await settle(component);

    component.find('tr.instance-type-row').at(0).simulate('click');
    await settle(component);

    expect(command.instanceType).toBe('m5.large');
    expect(command.viewState.dirty.instanceType).toBeUndefined();
    expect(command.viewState.instanceTypeDetails).toBe(instanceTypeDetails as any);
    expect(onTypeChanged).toHaveBeenCalledWith('m5.large');
  });

  it('recomputes unavailable types when filtered instance types are replaced', async () => {
    const instanceTypeService = serviceWithCategories();
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService as any);
    const command = commandWithFilteredTypes(['m5.large']);

    const component = mount(<InstanceTypeSelector command={command as any} onTypeChanged={jasmine.createSpy()} />);
    await settle(component);

    expect(component.find('tr.instance-type-row').at(1).hasClass('unavailable')).toBe(true);

    command.backingData.filtered.instanceTypes = ['m5.xlarge'];
    component.setProps({ command });
    await settle(component);

    expect(component.find('tr.instance-type-row').at(0).hasClass('unavailable')).toBe(true);
    expect(component.find('tr.instance-type-row').at(1).hasClass('unavailable')).toBe(false);
  });

  it('shows dirty warning, unavailable marker, and storage override display', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(serviceWithCategories() as any);
    const command = commandWithFilteredTypes(['m5.large']);
    command.instanceType = 'm5.large';
    command.viewState.overriddenStorageDescription = 'Custom storage';

    const component = mount(<InstanceTypeSelector command={command as any} onTypeChanged={jasmine.createSpy()} />);
    await settle(component);

    expect(component.find('.dirty-instance-type-warning').text()).toContain('previously selected instance type');
    expect(component.find('tr.instance-type-row').at(1).hasClass('unavailable')).toBe(true);
    expect(component.find('tr.instance-type-row').at(1).find('.unavailable-marker').exists()).toBe(true);
    expect(component.find('tr.instance-type-row').at(0).text()).toContain('Custom storage');
    expect(component.find('tr.instance-type-row').at(0).find('.storage-override-indicator').exists()).toBe(true);
  });

  it('hides the dirty warning immediately when dismissed', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(serviceWithCategories() as any);
    const command = commandWithFilteredTypes(['m5.large']);

    const component = mount(<InstanceTypeSelector command={command as any} onTypeChanged={jasmine.createSpy()} />);
    await settle(component);

    expect(component.find('.dirty-instance-type-warning').exists()).toBe(true);

    component.find('button.dirty-flag-dismiss').simulate('click');

    expect(command.viewState.dirty.instanceType).toBeNull();
    expect(component.find('.dirty-instance-type-warning').exists()).toBe(false);
  });
});

function commandWithFilteredTypes(instanceTypes: string[]) {
  return {
    selectedProvider: 'aws',
    backingData: { filtered: { instanceTypes } },
    viewState: { dirty: { instanceType: true }, instanceProfile: 'general' },
  };
}

function serviceWithCategories(details = { name: 'm5.large' }) {
  return {
    getCategories: jasmine.createSpy('getCategories').and.returnValue(
      Promise.resolve([
        {
          type: 'general',
          label: 'General',
          families: [
            {
              type: 'm5',
              instanceTypes: [
                { name: 'm5.large', label: 'large', cpu: 2, memory: 8, storage: { type: 'SSD', count: 1, size: 20 } },
                {
                  name: 'm5.xlarge',
                  label: 'xlarge',
                  cpu: 4,
                  memory: 16,
                  storage: { type: 'SSD', count: 1, size: 40 },
                },
              ],
            },
          ],
        },
      ]),
    ),
    getInstanceTypeDetails: jasmine.createSpy('getInstanceTypeDetails').and.returnValue(Promise.resolve(details)),
  };
}

async function settle(component: any) {
  await Promise.resolve();
  await Promise.resolve();
  component.update();
}
