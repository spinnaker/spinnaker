import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { HelpTextExpandedContext } from '@spinnaker/core';

import {
  GceInstanceFlexibilityConfigurer,
  hasValidFlexibilityPolicy,
  nextSelectionName,
} from './GceInstanceFlexibilityConfigurer';
import { setupUser } from '../../../../../../core/src/utils/testUtils/userEvent';
import '../../../../help/gce.help';

describe('GceInstanceFlexibilityConfigurer', () => {
  const policy = {
    instanceSelections: {
      preferred: { rank: 1, machineTypes: ['n2-standard-8'] },
    },
  };

  it('links the flexibility editor to its help content', () => {
    render(
      <HelpTextExpandedContext.Provider value={true}>
        <GceInstanceFlexibilityConfigurer
          regional={true}
          targetShape="BALANCED"
          setInstanceFlexibilityPolicy={vi.fn()}
        />
      </HelpTextExpandedContext.Provider>,
    );

    expect(
      screen.getByText(/Defines named sets of acceptable machine types for a regional managed instance group/),
    ).toBeInTheDocument();
  });

  it('allows EVEN when flexibility is absent', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'EVEN' },
      }),
    ).toBe(true);
  });

  it('blocks zonal and EVEN combinations when selections exist', () => {
    const policy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    };
    expect(
      hasValidFlexibilityPolicy({
        regional: false,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: policy,
      }),
    ).toBe(false);
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'EVEN' },
        instanceFlexibilityPolicy: policy,
      }),
    ).toBe(false);
  });

  it('rejects blank machine type placeholders', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: [''] },
          },
        },
      }),
    ).toBe(false);
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8', ''] },
          },
        },
      }),
    ).toBe(false);
  });

  it('rejects duplicate machine types within and across selections', () => {
    [
      {
        preferred: { machineTypes: ['e2-standard-2', 'e2-standard-2'] },
      },
      {
        preferred: { machineTypes: ['e2-standard-2'] },
        fallback: { machineTypes: ['e2-standard-2'] },
      },
    ].forEach((instanceSelections) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape: 'BALANCED' },
          instanceFlexibilityPolicy: { instanceSelections },
        }),
      ).toBe(false);
    });
  });

  it('normalizes duplicate machine types and displays a warning', () => {
    const duplicatePolicy = {
      instanceSelections: {
        preferred: {
          machineTypes: [
            ' https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a/machineTypes/E2-STANDARD-2 ',
          ],
        },
        fallback: { machineTypes: ['zones/us-central1-b/machineTypes/e2-standard-2'] },
      },
    };

    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: duplicatePolicy,
      }),
    ).toBe(false);

    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={duplicatePolicy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={vi.fn()}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(/^Machine types must be unique across instance selections\.$/);
  });

  it('accepts distinct machine types across selections', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: ['e2-standard-2', 'n2-standard-2'] },
            fallback: { machineTypes: ['c2-standard-4'] },
          },
        },
      }),
    ).toBe(true);
  });

  it('rejects ranks that are not finite non-negative integers', () => {
    [-1, 1.5, Number.NaN, Number.POSITIVE_INFINITY].forEach((rank) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape: 'BALANCED' },
          instanceFlexibilityPolicy: {
            instanceSelections: {
              preferred: { rank, machineTypes: ['n2-standard-8'] },
            },
          },
        }),
      ).toBe(false);
    });
  });

  it('announces submission errors and associates malformed persisted controls', () => {
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { rank: -1, machineTypes: ['', 'n2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        validationError="Instance flexibility policy is invalid."
        setInstanceFlexibilityPolicy={vi.fn()}
      />,
    );
    const alert = screen.getByRole('alert');
    const rankInput = screen.getByLabelText('Rank (optional)');
    const blankMachineType = screen.getByRole('textbox', { name: 'Machine type 1 for selection preferred' });
    const validMachineType = screen.getByRole('textbox', { name: 'Machine type 2 for selection preferred' });

    expect(alert).toHaveTextContent(/^Instance flexibility policy is invalid\.$/);
    expect(rankInput).toBeInvalid();
    expect(rankInput).toHaveAccessibleDescription('Instance flexibility policy is invalid.');
    expect(blankMachineType).toBeInvalid();
    expect(blankMachineType).toHaveAccessibleDescription('Instance flexibility policy is invalid.');
    expect(validMachineType).toBeValid();
    expect(validMachineType).not.toHaveAttribute('aria-describedby');
  });

  it('accepts regional BALANCED/ANY/ANY_SINGLE_ZONE with rankless selections', () => {
    const policy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
        fallback: { machineTypes: ['e2-standard-8'] },
      },
    };
    ['BALANCED', 'ANY', 'ANY_SINGLE_ZONE', ' balanced '].forEach((targetShape) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape },
          instanceFlexibilityPolicy: policy,
        }),
      ).toBe(true);
    });
  });

  it('chooses the first unused selection name', () => {
    expect(nextSelectionName([])).toBe('selection-1');
    expect(nextSelectionName(['selection-1', 'preferred'])).toBe('selection-2');
    expect(nextSelectionName(['selection-1', 'selection-2'])).toBe('selection-3');
    expect(nextSelectionName(['selection-2'])).toBe('selection-1');
  });

  it('adds a named selection through the configurer', async () => {
    const user = setupUser();
    const setPolicy = vi.fn();
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={undefined}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Add flexibility policy' }));

    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        'selection-1': { machineTypes: [''] },
      },
    });
  });

  it('does not overwrite an existing renamed selection when adding another', async () => {
    const user = setupUser();
    const setPolicy = vi.fn();
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            'selection-1': { machineTypes: ['n2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Add selection' }));

    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        'selection-1': { machineTypes: ['n2-standard-8'] },
        'selection-2': { machineTypes: [''] },
      },
    });
  });

  it('sends an explicit empty policy when the final selection is removed', async () => {
    const user = setupUser();
    const setPolicy = vi.fn();
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Remove selection preferred' }));

    expect(setPolicy).toHaveBeenCalledWith({ instanceSelections: {} });
  });

  it('only persists finite non-negative integer ranks and supports rank zero and clearing', () => {
    const setPolicy = vi.fn();
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    const rankInput = screen.getByRole('spinbutton', { name: 'Rank (optional)' });

    expect(rankInput).toHaveAttribute('step', '1');

    // Non-numeric text cannot be entered into a number input (the DOM sanitizes it to ''), so only
    // fractional and negative values are exercised as rejected ranks.
    fireEvent.change(rankInput, { target: { value: '1.5' } });
    fireEvent.change(rankInput, { target: { value: '-1' } });
    expect(setPolicy).not.toHaveBeenCalled();

    fireEvent.change(rankInput, { target: { value: '0' } });
    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        preferred: { rank: 0, machineTypes: ['n2-standard-8'] },
      },
    });

    setPolicy.mockClear();
    fireEvent.change(rankInput, { target: { value: '' } });
    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    });
  });

  it('resets blank and duplicate rename drafts to the current valid selection name', async () => {
    const user = setupUser();
    const setPolicy = vi.fn();
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8'] },
            fallback: { machineTypes: ['e2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    const preferredNameInput = screen.getByDisplayValue('preferred');

    await user.clear(preferredNameInput);
    await user.type(preferredNameInput, ' ');
    await user.tab();
    expect(preferredNameInput).toHaveValue('preferred');

    await user.clear(preferredNameInput);
    await user.type(preferredNameInput, 'fallback');
    await user.tab();
    expect(preferredNameInput).toHaveValue('preferred');
    expect(setPolicy).not.toHaveBeenCalled();
  });

  it('associates labels and contextual accessible names with selection controls', () => {
    render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={vi.fn()}
      />,
    );
    const selectionId = 'instance-flexibility-selection-preferred';

    expect(screen.getByRole('textbox', { name: 'Selection name' })).toHaveAttribute('id', `${selectionId}-name`);
    expect(screen.getByRole('spinbutton', { name: 'Rank (optional)' })).toHaveAttribute('id', `${selectionId}-rank`);
    expect(screen.getByRole('textbox', { name: 'Machine type 1 for selection preferred' })).toHaveAttribute(
      'id',
      `${selectionId}-machine-type-0`,
    );
    expect(screen.getByRole('button', { name: 'Remove selection preferred' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remove machine type 1 from selection preferred' })).toBeInTheDocument();
  });

  it('rerenders when policy and sibling primitive props change identity', () => {
    const setPolicy = vi.fn();
    const { rerender } = render(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={undefined}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    expect(screen.getByRole('button', { name: 'Add flexibility policy' })).toBeInTheDocument();

    rerender(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    expect(screen.getByRole('button', { name: 'Remove selection preferred' })).toBeInTheDocument();

    rerender(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={false}
        targetShape="EVEN"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    expect(screen.getByText('Flexibility requires a regional server group.')).toBeInTheDocument();
    expect(screen.getByText(/not EVEN/)).toBeInTheDocument();
  });
});
