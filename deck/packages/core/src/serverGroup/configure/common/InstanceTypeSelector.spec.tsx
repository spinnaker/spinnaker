import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { InstanceTypeSelector } from './InstanceTypeSelector';

describe('InstanceTypeSelector', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );

  beforeEach(() => {
    runtimeServices = {};
    Object.defineProperty(runtimeServices, 'instanceTypeService', { configurable: true, get: () => undefined });
  });

  const renderSelector = (command: any, onTypeChanged = vi.fn()) =>
    render(<InstanceTypeSelector command={command} onTypeChanged={onTypeChanged} />, { wrapper: RuntimeWrapper });

  it('renders the native selector and ignores unavailable instance types', async () => {
    const instanceTypeService = serviceWithCategories();
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService as any);
    const command = commandWithFilteredTypes(['m5.large']);
    renderSelector(command);

    await userEvent.click(await screen.findByText('xlarge'));

    expect(command.instanceType).toBeUndefined();
    expect(instanceTypeService.getInstanceTypeDetails).not.toHaveBeenCalled();
  });

  it('selects an available instance type, clears dirty state, loads details, and notifies', async () => {
    const instanceTypeDetails = { name: 'm5.large' };
    const instanceTypeService = serviceWithCategories(instanceTypeDetails);
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService as any);
    const onTypeChanged = vi.fn();
    const command = commandWithFilteredTypes(['m5.large']);
    renderSelector(command, onTypeChanged);

    await userEvent.click(await screen.findByText('large'));

    expect(command.instanceType).toBe('m5.large');
    expect(command.viewState.dirty.instanceType).toBeUndefined();
    await waitFor(() => expect(command.viewState.instanceTypeDetails).toBe(instanceTypeDetails as any));
    expect(onTypeChanged).toHaveBeenCalledWith('m5.large');
  });

  it('recomputes unavailable types when filtered instance types are replaced', async () => {
    const instanceTypeService = serviceWithCategories();
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(instanceTypeService as any);
    const command = commandWithFilteredTypes(['m5.large']);
    const { rerender } = renderSelector(command);

    expect((await screen.findByText('xlarge')).closest('tr')).toHaveClass('unavailable');

    command.backingData.filtered.instanceTypes = ['m5.xlarge'];
    rerender(<InstanceTypeSelector command={command as any} onTypeChanged={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('large').closest('tr')).toHaveClass('unavailable'));
    expect(screen.getByText('xlarge').closest('tr')).not.toHaveClass('unavailable');
  });

  it('shows dirty warning, unavailable marker, and storage override display', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(serviceWithCategories() as any);
    const command = commandWithFilteredTypes(['m5.large']);
    command.instanceType = 'm5.large';
    command.viewState.overriddenStorageDescription = 'Custom storage';
    const { container } = renderSelector(command);

    expect(await screen.findByText(/previously selected instance type/)).toBeInTheDocument();
    const unavailableRow = screen.getByText('xlarge').closest('tr');
    expect(unavailableRow).toHaveClass('unavailable');
    expect(unavailableRow?.querySelector('.unavailable-marker')).toBeInTheDocument();
    expect(screen.getByText('Custom storage')).toBeInTheDocument();
    expect(container.querySelector('.storage-override-indicator')).toBeInTheDocument();
  });

  it('hides the dirty warning immediately when dismissed', async () => {
    vi.spyOn(runtimeServices, 'instanceTypeService', 'get').mockReturnValue(serviceWithCategories() as any);
    const command = commandWithFilteredTypes(['m5.large']);
    renderSelector(command);

    expect(await screen.findByText(/previously selected instance type/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Okay' }));

    expect(command.viewState.dirty.instanceType).toBeNull();
    expect(screen.queryByText(/previously selected instance type/)).not.toBeInTheDocument();
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
    getCategories: vi.fn().mockReturnValue(
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
    getInstanceTypeDetails: vi.fn().mockReturnValue(Promise.resolve(details)),
  };
}
