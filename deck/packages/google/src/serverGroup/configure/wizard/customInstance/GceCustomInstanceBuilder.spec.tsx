import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { DeckRuntimeContext } from '@spinnaker/core';
import { GceCustomInstanceBuilder } from './GceCustomInstanceBuilder';

vi.mock('./CustomInstanceConfigurer', () => ({
  CustomInstanceConfigurer: (props: any) => (
    <div
      data-testid="custom-instance-configurer"
      data-extended={String(props.selectedExtendedMemory)}
      data-family={props.selectedInstanceFamily}
      data-memory={String(props.selectedMemory)}
      data-vcpu={String(props.selectedVCpuCount)}
    >
      <span>{`families:${props.instanceFamilyList.join(',')}`}</span>
      <span>{`cpus:${props.vCpuList.join(',')}`}</span>
      <span>{`memory:${props.memoryList.join(',')}`}</span>
      <button
        onClick={() => props.onChange({ instanceFamily: 'N2', vCpuCount: 8, memory: 32, extendedMemory: false })}
        type="button"
      >
        Select N2 custom type
      </button>
      <button
        onClick={() => props.onChange({ instanceFamily: 'N1', vCpuCount: null, memory: 4, extendedMemory: false })}
        type="button"
      >
        Select memory before cores
      </button>
    </div>
  ),
}));

describe('GceCustomInstanceBuilder', () => {
  let runtimeServices: any;

  beforeEach(() => {
    runtimeServices = {};
  });

  it('parses the current instance type and provides valid custom instance choices', () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = commandWithCustomInstance('n2-custom-4-16384-ext');

    renderBuilder(command, vi.fn());
    const configurer = screen.getByTestId('custom-instance-configurer');

    expect(configurer).toHaveAttribute('data-family', 'N2');
    expect(configurer).toHaveAttribute('data-vcpu', '4');
    expect(configurer).toHaveAttribute('data-memory', '16');
    expect(configurer).toHaveAttribute('data-extended', 'true');
    expect(screen.getByText(/families:.*N2D/)).toBeInTheDocument();
    expect(screen.getByText(/cpus:.*4/)).toBeInTheDocument();
    expect(screen.getByText(/memory:.*16/)).toBeInTheDocument();
  });

  it('updates command.instanceType, notifies, and loads details when custom choices change', async () => {
    const instanceTypeDetails = { name: 'n2-custom-8-32768' };
    runtimeServices.instanceTypeService = instanceTypeService(instanceTypeDetails);
    const onTypeChanged = vi.fn();
    const command = commandWithCustomInstance('n2-custom-4-16384');
    renderBuilder(command, onTypeChanged);

    fireEvent.click(screen.getByRole('button', { name: 'Select N2 custom type' }));

    expect(command.instanceType).toBe('n2-custom-8-32768');
    expect(command.customInstanceChanged).toHaveBeenCalledWith(command);
    expect(onTypeChanged).toHaveBeenCalledWith('n2-custom-8-32768');
    await waitFor(() => expect(command.viewState.instanceTypeDetails).toBe(instanceTypeDetails));
  });

  it('initializes missing custom values from valid lists', () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const command = commandWithCustomInstance(null);

    renderBuilder(command, vi.fn());
    const configurer = screen.getByTestId('custom-instance-configurer');

    expect(configurer).toHaveAttribute('data-family', 'N1');
    expect(configurer).toHaveAttribute('data-vcpu', '1');
    expect(configurer).toHaveAttribute('data-memory', '1');
    expect(command.viewState.customInstance).toEqual({
      extendedMemory: false,
      instanceFamily: 'N1',
      memory: 1,
      vCpuCount: 1,
    });
  });

  it('keeps valid defaults when memory changes before cores', () => {
    runtimeServices.instanceTypeService = instanceTypeService();
    const onTypeChanged = vi.fn();
    const command = commandWithCustomInstance(null);
    renderBuilder(command, onTypeChanged);

    fireEvent.click(screen.getByRole('button', { name: 'Select memory before cores' }));

    expect(command.instanceType).toBe('custom-1-4096');
    expect(onTypeChanged).toHaveBeenCalledWith('custom-1-4096');
  });

  function renderBuilder(
    command: ReturnType<typeof commandWithCustomInstance>,
    onTypeChanged: ReturnType<typeof vi.fn>,
  ) {
    return render(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
        <GceCustomInstanceBuilder command={command as any} onTypeChanged={onTypeChanged} />
      </DeckRuntimeContext.Provider>,
    );
  }
});

function commandWithCustomInstance(instanceType: string | null) {
  return {
    selectedProvider: 'gce',
    instanceType,
    region: 'us-central1',
    regional: true,
    viewState: {} as any,
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
    customInstanceChanged: vi.fn(),
  };
}

function instanceTypeService(details = { name: 'n2-custom-8-32768' }) {
  return {
    getInstanceTypeDetails: vi.fn().mockResolvedValue(details),
  };
}
