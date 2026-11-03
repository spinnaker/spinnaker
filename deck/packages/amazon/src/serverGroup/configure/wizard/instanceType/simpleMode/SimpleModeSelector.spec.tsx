import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { DeckRuntimeContext } from '@spinnaker/core';

import { SimpleModeSelector } from './SimpleModeSelector';

describe('SimpleModeSelector', () => {
  function renderSelector(command = buildCommand()) {
    return {
      command,
      ...render(
        <DeckRuntimeContext.Provider value={{ services: { instanceTypeService: instanceTypeService() } } as any}>
          <SimpleModeSelector
            command={command as any}
            setUnlimitedCpuCredits={vi.fn()}
            setFieldValue={vi.fn()}
            clearWarnings={vi.fn()}
          />
        </DeckRuntimeContext.Provider>,
      ),
    };
  }

  it('shows instance type rows when an instance profile tile is clicked', async () => {
    renderSelector();
    expect(screen.queryByText('m5.large')).not.toBeInTheDocument();

    await userEvent.click(await screen.findByRole('button', { name: /General Purpose/i }));

    expect(await screen.findByText('m5.large')).toBeInTheDocument();
  });

  it('updates derived command fields from the selected instance type', async () => {
    const { command } = renderSelector();
    await userEvent.click(await screen.findByRole('button', { name: /General Purpose/i }));
    await userEvent.click(await screen.findByText('m5.large'));

    await waitFor(() =>
      expect(command.instanceTypeChanged).toHaveBeenCalledWith(expect.objectContaining({ instanceType: 'm5.large' })),
    );
  });
});

function buildCommand() {
  return {
    backingData: { filtered: { instanceTypes: ['m5.large'] } },
    instanceTypeChanged: vi.fn(),
    selectedProvider: 'aws',
    viewState: { dirty: {} },
  };
}

function instanceTypeService() {
  return {
    getCategories: vi.fn().mockResolvedValue([
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
    getInstanceTypeDetails: vi.fn().mockResolvedValue({}),
  };
}
