import { mount as enzymeMount } from 'enzyme';
import React from 'react';

import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { CloudProviderRegistry } from '../../../cloudProvider';
import { ModalWizard } from '../../../modal/wizard/ModalWizard';
import { InstanceArchetypeSelector } from './InstanceArchetypeSelector';
import { v2InstanceArchetypeSelector } from './v2instanceArchetypeSelector.component';

describe('InstanceArchetypeSelector', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
    Object.defineProperty(runtimeServices, 'instanceTypeService', { configurable: true, get: () => undefined });
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);
  });

  afterEach(() => {
    ModalWizard.renderedPages = [];
    ModalWizard.pageRegistry = [];
  });

  it('registers the Angular v2 component through the React bridge', () => {
    expect(v2InstanceArchetypeSelector.templateUrl).toBeUndefined();
    expect(v2InstanceArchetypeSelector.controller).toBeDefined();
  });

  it('renders without the AngularJS adapter and mutates the selected profile', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const onProfileChanged = jasmine.createSpy('onProfileChanged');
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={onProfileChanged}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    expect(component.find(`.Angular${'JS'}Adapter`).exists()).toBe(false);
    component.find('button.instance-profile').simulate('click');

    expect(command.viewState.instanceProfile).toBe('general');
    expect(onProfileChanged).toHaveBeenCalledWith('general');
  });

  it('shows the selected profile indicator after a profile is clicked', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    component.find('button.instance-profile').simulate('click');
    component.update();

    expect(component.find('.selected-indicator').exists()).toBe(true);
  });

  it('keeps the selected profile when the command prop is replaced with the selected profile', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    component.find('button.instance-profile').simulate('click');
    await settle(component);

    component.setProps({
      command: { ...command, viewState: { ...command.viewState, instanceProfile: 'general' } },
    });
    await settle(component);

    expect(component.find('button.instance-profile').hasClass('active')).toBe(true);
    expect(component.find('.selected-indicator').exists()).toBe(true);
  });

  it('provides the direct React layout hooks for inline archetype columns', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(
      instanceTypeService('general', 3) as any,
    );
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    expect(component.find('.instance-archetype-selector').exists()).toBe(true);
    expect(component.find('.archetype-columns').first().hasClass('archetype-columns-3')).toBe(true);
  });

  it('uses the old three-column layout for six profile providers', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(
      instanceTypeService('general', 6) as any,
    );
    const command = { selectedProvider: 'gce', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    expect(component.find('.archetype-columns').first().hasClass('archetype-columns-3')).toBe(true);
  });

  it('clears the current instance type when selecting a non-custom profile that does not contain it', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: 'c5.large',
      viewState: {},
      backingData: { filtered: { instanceTypes: [] } },
    } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    component.find('button.instance-profile').simulate('click');

    expect(command.instanceType).toBeNull();
  });

  it('uses the profile selection path for initial custom selection', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const onProfileChanged = jasmine.createSpy('onProfileChanged');
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      region: 'us-east-1',
      instanceType: 'm5.large',
      viewState: {},
      backingData: { filtered: { instanceTypes: ['m5.large'] } },
    } as any;

    mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={onProfileChanged}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await Promise.resolve();

    expect(command.viewState.instanceProfile).toBe('custom');
    expect(onProfileChanged).toHaveBeenCalledWith('custom');
  });

  it('renders a registered React custom instance builder for buildCustom profiles', async () => {
    const CustomInstanceBuilder = jasmine.createSpy('CustomInstanceBuilder').and.callFake(() => null);
    (CloudProviderRegistry.getValue as any).and.callFake((_provider: string, key: string) =>
      key === 'instance.CustomInstanceBuilder' ? CustomInstanceBuilder : null,
    );
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(
      instanceTypeService('buildCustom') as any,
    );
    const command = {
      selectedProvider: 'gce',
      cloudProvider: 'gce',
      viewState: {},
      backingData: { filtered: { instanceTypes: [] } },
    } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    component.find('button.instance-profile').simulate('click');

    expect(component.find(CustomInstanceBuilder).exists()).toBe(true);
    expect(component.find(CustomInstanceBuilder).prop('command')).toBe(command);
  });

  it('shows dirty warning in the custom instance type path', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService('custom') as any);
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      viewState: { dirty: { instanceType: 'm5.large' }, instanceProfile: 'custom' },
      backingData: { filtered: { instanceTypes: ['m5.xlarge'] } },
    } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    expect(component.find('.dirty-instance-type-warning').text()).toContain('previously selected instance type');
  });

  it('uses the selection path for an initial profile without notifying unchanged profile', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService() as any);
    const onProfileChanged = jasmine.createSpy('onProfileChanged');
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: 'c5.large',
      viewState: { instanceProfile: 'general' },
      backingData: { filtered: { instanceTypes: [] } },
    } as any;

    mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={onProfileChanged}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await Promise.resolve();

    expect(command.instanceType).toBeNull();
    expect(onProfileChanged).not.toHaveBeenCalled();
  });

  it('marks the instance type wizard page complete or incomplete when instance type changes', async () => {
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(instanceTypeService('custom') as any);
    spyOn(ModalWizard, 'markComplete');
    spyOn(ModalWizard, 'markIncomplete');
    ModalWizard.pageRegistry = [{ key: 'instance-type', state: { done: false } } as any];
    ModalWizard.renderedPages = ModalWizard.pageRegistry;
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: null,
      viewState: { instanceProfile: 'custom' },
      backingData: { filtered: { instanceTypes: ['m5.large'] } },
    } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={jasmine.createSpy()}
      />,
    );
    await settle(component);

    expect(ModalWizard.markIncomplete).toHaveBeenCalledWith('instance-type');

    component.find('select.custom-instance-type').simulate('change', { target: { value: 'm5.large' } });

    expect(ModalWizard.markComplete).toHaveBeenCalledWith('instance-type');
  });

  it('marks the instance type wizard page complete when buildCustom updates the instance type', async () => {
    const CustomInstanceBuilder = jasmine.createSpy('CustomInstanceBuilder').and.callFake(() => null);
    (CloudProviderRegistry.getValue as any).and.callFake((_provider: string, key: string) =>
      key === 'instance.CustomInstanceBuilder' ? CustomInstanceBuilder : null,
    );
    spyOnProperty(runtimeServices, 'instanceTypeService', 'get').and.returnValue(
      instanceTypeService('buildCustom') as any,
    );
    spyOn(ModalWizard, 'markComplete');
    ModalWizard.pageRegistry = [{ key: 'instance-type', state: { done: false } } as any];
    ModalWizard.renderedPages = ModalWizard.pageRegistry;
    const onTypeChanged = jasmine.createSpy('onTypeChanged');
    const command = {
      selectedProvider: 'gce',
      cloudProvider: 'gce',
      viewState: { instanceProfile: 'buildCustom' },
      backingData: { filtered: { instanceTypes: [] } },
    } as any;

    const component = mount(
      <InstanceArchetypeSelector
        command={command}
        onProfileChanged={jasmine.createSpy()}
        onTypeChanged={onTypeChanged}
      />,
    );
    await settle(component);

    command.instanceType = 'n2-custom-4-16384';
    component.find(CustomInstanceBuilder).prop('onTypeChanged')('n2-custom-4-16384');

    expect(ModalWizard.markComplete).toHaveBeenCalledWith('instance-type');
    expect(onTypeChanged).toHaveBeenCalledWith('n2-custom-4-16384');
  });
});

function instanceTypeService(type = 'general', count = 1) {
  return {
    getCategories: jasmine.createSpy('getCategories').and.returnValue(
      Promise.resolve(
        Array.from({ length: count }, (_unused, index) => ({
          type: index === 0 ? type : `${type}-${index}`,
          label: `General ${index + 1}`,
          icon: 'cloud',
          description: 'General purpose',
          stats: {
            families: ['m5'],
            cpu: { min: 2, max: 4 },
            memory: { min: 8, max: 16 },
            storage: { min: 20, max: 40 },
          },
          families: [{ type: 'm5', instanceTypes: [{ name: 'm5.large' }] }],
        })),
      ),
    ),
    getInstanceTypeDetails: jasmine.createSpy('getInstanceTypeDetails').and.returnValue(Promise.resolve({})),
  };
}

async function settle(component: any) {
  await Promise.resolve();
  await Promise.resolve();
  component.update();
}
