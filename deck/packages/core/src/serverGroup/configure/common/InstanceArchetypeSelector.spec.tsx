import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { CloudProviderRegistry } from '../../../cloudProvider';
import { ModalWizard } from '../../../modal/wizard/ModalWizard';
import { InstanceArchetypeSelector } from './InstanceArchetypeSelector';

describe('InstanceArchetypeSelector', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );

  beforeEach(() => {
    runtimeServices = {};
    Object.defineProperty(runtimeServices, 'instanceTypeService', { configurable: true, get: () => undefined });
    vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(null);
  });

  afterEach(() => {
    ModalWizard.renderedPages = [];
    ModalWizard.pageRegistry = [];
  });

  const renderSelector = (command: any, onProfileChanged = vi.fn(), onTypeChanged = vi.fn()) =>
    render(
      <InstanceArchetypeSelector command={command} onProfileChanged={onProfileChanged} onTypeChanged={onTypeChanged} />,
      { wrapper: RuntimeWrapper },
    );

  it('renders the native selector and mutates the selected profile', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const onProfileChanged = vi.fn();
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;

    renderSelector(command, onProfileChanged);
    await userEvent.click(await screen.findByRole('button', { name: /General 1/ }));

    expect(command.viewState.instanceProfile).toBe('general');
    expect(onProfileChanged).toHaveBeenCalledWith('general');
  });

  it('shows the selected profile indicator after a profile is clicked', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;
    const { container } = renderSelector(command);

    await userEvent.click(await screen.findByRole('button', { name: /General 1/ }));

    expect(container.querySelector('.selected-indicator')).toBeInTheDocument();
  });

  it('keeps the selected profile when the command prop is replaced with the selected profile', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;
    const { container, rerender } = renderSelector(command);

    await userEvent.click(await screen.findByRole('button', { name: /General 1/ }));
    const nextCommand = { ...command, viewState: { ...command.viewState, instanceProfile: 'general' } };
    rerender(<InstanceArchetypeSelector command={nextCommand} onProfileChanged={vi.fn()} onTypeChanged={vi.fn()} />);

    await waitFor(() => expect(screen.getByRole('button', { name: /General 1/ })).toHaveClass('active'));
    expect(container.querySelector('.selected-indicator')).toBeInTheDocument();
  });

  it('provides the direct React layout hooks for inline archetype columns', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('general', 3) as any);
    const command = { selectedProvider: 'aws', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;
    const { container } = renderSelector(command);

    await screen.findAllByRole('button', { name: /General/ });

    expect(container.querySelector('.instance-archetype-selector')).toBeInTheDocument();
    expect(container.querySelector('.archetype-columns')).toHaveClass('archetype-columns-3');
  });

  it('uses the old three-column layout for six profile providers', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('general', 6) as any);
    const command = { selectedProvider: 'gce', viewState: {}, backingData: { filtered: { instanceTypes: [] } } } as any;
    const { container } = renderSelector(command);

    await screen.findAllByRole('button', { name: /General/ });

    expect(container.querySelector('.archetype-columns')).toHaveClass('archetype-columns-3');
  });

  it('clears the current instance type when selecting a non-custom profile that does not contain it', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: 'c5.large',
      viewState: {},
      backingData: { filtered: { instanceTypes: [] } },
    } as any;
    renderSelector(command);

    await userEvent.click(await screen.findByRole('button', { name: /General 1/ }));

    expect(command.instanceType).toBeNull();
  });

  it('uses the profile selection path for initial custom selection', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const onProfileChanged = vi.fn();
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      region: 'us-east-1',
      instanceType: 'm5.large',
      viewState: {},
      backingData: { filtered: { instanceTypes: ['m5.large'] } },
    } as any;

    renderSelector(command, onProfileChanged);

    await waitFor(() => expect(command.viewState.instanceProfile).toBe('custom'));
    expect(onProfileChanged).toHaveBeenCalledWith('custom');
  });

  it('renders a registered React custom instance builder for buildCustom profiles', async () => {
    const CustomInstanceBuilder = ({ command }: any) => <div>Custom builder for {command.selectedProvider}</div>;
    (CloudProviderRegistry.getValue as any).mockImplementation((_provider: string, key: string) =>
      key === 'instance.CustomInstanceBuilder' ? CustomInstanceBuilder : null,
    );
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('buildCustom') as any);
    const command = {
      selectedProvider: 'gce',
      cloudProvider: 'gce',
      viewState: {},
      backingData: { filtered: { instanceTypes: [] } },
    } as any;
    renderSelector(command);

    await userEvent.click(await screen.findByRole('button', { name: /General 1/ }));

    expect(screen.getByText('Custom builder for gce')).toBeInTheDocument();
  });

  it('shows dirty warning in the custom instance type path', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('custom') as any);
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      viewState: { dirty: { instanceType: 'm5.large' }, instanceProfile: 'custom' },
      backingData: { filtered: { instanceTypes: ['m5.xlarge'] } },
    } as any;

    renderSelector(command);

    expect(await screen.findByText(/previously selected instance type/)).toBeInTheDocument();
  });

  it('uses the selection path for an initial profile without notifying unchanged profile', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService() as any);
    const onProfileChanged = vi.fn();
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: 'c5.large',
      viewState: { instanceProfile: 'general' },
      backingData: { filtered: { instanceTypes: [] } },
    } as any;

    renderSelector(command, onProfileChanged);

    await waitFor(() => expect(command.instanceType).toBeNull());
    expect(onProfileChanged).not.toHaveBeenCalled();
  });

  it('marks the instance type wizard page complete or incomplete when instance type changes', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('custom') as any);
    vi.spyOn(ModalWizard, 'markComplete').mockReturnValue(undefined);
    vi.spyOn(ModalWizard, 'markIncomplete').mockReturnValue(undefined);
    ModalWizard.pageRegistry = [{ key: 'instance-type', state: { done: false } } as any];
    ModalWizard.renderedPages = ModalWizard.pageRegistry;
    const command = {
      selectedProvider: 'aws',
      cloudProvider: 'aws',
      instanceType: null,
      viewState: { instanceProfile: 'custom' },
      backingData: { filtered: { instanceTypes: ['m5.large'] } },
    } as any;

    renderSelector(command);
    await waitFor(() => expect(ModalWizard.markIncomplete).toHaveBeenCalledWith('instance-type'));

    await userEvent.selectOptions(screen.getByRole('combobox'), 'm5.large');

    expect(ModalWizard.markComplete).toHaveBeenCalledWith('instance-type');
  });

  it('marks the instance type wizard page complete when buildCustom updates the instance type', async () => {
    const CustomInstanceBuilder = ({ command, onTypeChanged }: any) => (
      <button
        onClick={() => {
          command.instanceType = 'n2-custom-4-16384';
          onTypeChanged('n2-custom-4-16384');
        }}
      >
        Build custom instance
      </button>
    );
    (CloudProviderRegistry.getValue as any).mockImplementation((_provider: string, key: string) =>
      key === 'instance.CustomInstanceBuilder' ? CustomInstanceBuilder : null,
    );
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService('buildCustom') as any);
    vi.spyOn(ModalWizard, 'markComplete').mockReturnValue(undefined);
    ModalWizard.pageRegistry = [{ key: 'instance-type', state: { done: false } } as any];
    ModalWizard.renderedPages = ModalWizard.pageRegistry;
    const onTypeChanged = vi.fn();
    const command = {
      selectedProvider: 'gce',
      cloudProvider: 'gce',
      viewState: { instanceProfile: 'buildCustom' },
      backingData: { filtered: { instanceTypes: [] } },
    } as any;
    renderSelector(command, vi.fn(), onTypeChanged);

    await userEvent.click(await screen.findByRole('button', { name: 'Build custom instance' }));

    expect(ModalWizard.markComplete).toHaveBeenCalledWith('instance-type');
    expect(onTypeChanged).toHaveBeenCalledWith('n2-custom-4-16384');
  });
});

function instanceTypeService(type = 'general', count = 1) {
  return {
    getCategories: vi.fn().mockReturnValue(
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
    getInstanceTypeDetails: vi.fn().mockReturnValue(Promise.resolve({})),
  };
}
