import type { Mock } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { DeckRuntimeContext, ReactModal, TaskExecutor } from '@spinnaker/core';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import { EditAsgAdvancedSettingsModal, validateRequiredNonNegativeNumber } from './EditAsgAdvancedSettingsModal';

describe('EditAsgAdvancedSettingsModal', () => {
  const application = { name: 'deck', serverGroups: { refresh: vi.fn() } } as any;
  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1' } as any;
  const modalProps = { application, serverGroup, closeModal: vi.fn(), dismissModal: vi.fn() };
  let buildUpdateServerGroupCommand: Mock;

  function renderModal(command: any) {
    buildUpdateServerGroupCommand = vi.fn().mockReturnValue(command);
    const runtime = {
      services: {
        providerServiceDelegate: {
          getDelegate: vi.fn().mockReturnValue({ buildUpdateServerGroupCommand }),
        },
      },
    } as any;
    const ui = (
      <DeckRuntimeContext.Provider value={runtime}>
        <EditAsgAdvancedSettingsModal {...modalProps} />
      </DeckRuntimeContext.Provider>
    );
    return { ...renderWithRouter(ui), ui };
  }

  function command() {
    return {
      cooldown: 10,
      enabledMetrics: ['GroupDesiredCapacity'],
      healthCheckGracePeriod: 600,
      healthCheckType: 'EC2',
      terminationPolicies: ['Default'],
      capacityRebalance: false,
      backingData: {
        enabledMetrics: ['GroupDesiredCapacity'],
        healthCheckTypes: ['EC2', 'ELB'],
        terminationPolicies: ['Default'],
      },
    } as any;
  }

  it('rejects empty and non-finite numeric values while accepting non-negative numbers', () => {
    [undefined, null, '', Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, -1].forEach((value) =>
      expect(validateRequiredNonNegativeNumber(value)).toBe('Enter a finite non-negative number'),
    );
    [0, 0.5, 1].forEach((value) => expect(validateRequiredNonNegativeNumber(value)).toBeUndefined());
  });

  it('builds one update command and preserves its form values across rerenders', () => {
    const rendered = renderModal(command());

    expect(screen.getByRole('spinbutton', { name: 'Cooldown' })).toHaveValue(10);
    rendered.rerender(rendered.ui);

    expect(buildUpdateServerGroupCommand).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('spinbutton', { name: 'Health Check Grace Period' })).toHaveValue(600);
  });

  it('renders every advanced setting and submits the exact task envelope', async () => {
    const values = command();
    const executeTask = vi.spyOn(TaskExecutor, 'executeTask').mockResolvedValue({} as any);
    renderModal(values);

    const controls = [
      screen.getByRole('spinbutton', { name: 'Cooldown' }),
      screen.getByRole('combobox', { name: 'Enabled Metrics' }),
      screen.getByRole('combobox', { name: 'Health Check Type' }),
      screen.getByRole('spinbutton', { name: 'Health Check Grace Period' }),
      screen.getByRole('combobox', { name: 'Termination Policies' }),
      screen.getByRole('checkbox', { name: 'Enable capacity rebalance' }),
    ];
    controls.forEach((control) => {
      expect(control.closest('.sp-formItem')).toHaveClass('sp-formItem');
      expect(control.closest('.sp-formItem')?.querySelector('.sp-formItem__right')).toContainElement(control);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
    await waitFor(() =>
      expect(executeTask).toHaveBeenCalledWith({
        application,
        description: 'Update Advanced Settings for deck-main-v001',
        job: [values],
      }),
    );
  });

  it('requires finite non-negative cooldown and health check grace period values', async () => {
    renderModal(command());
    const submit = screen.getByRole('button', { name: 'Submit' });

    for (const name of ['Cooldown', 'Health Check Grace Period']) {
      const input = screen.getByRole('spinbutton', { name });
      for (const invalidValue of ['', '-1', 'Infinity']) {
        fireEvent.change(input, { target: { value: invalidValue } });
        await waitFor(() => expect(submit).toBeDisabled());
        fireEvent.change(input, { target: { value: '0' } });
        await waitFor(() => expect(submit).toBeEnabled());
      }
    }
  });

  it('opens only after managed-resource verification succeeds', async () => {
    const show = vi.spyOn(ReactModal, 'show').mockResolvedValue(undefined as any);
    const runtimeServices = {} as any;

    await EditAsgAdvancedSettingsModal.show({ application, serverGroup }, runtimeServices);

    expect(show).toHaveBeenCalledWith(
      EditAsgAdvancedSettingsModal,
      { application, serverGroup },
      { dialogClassName: 'modal-lg' },
      runtimeServices,
    );
  });
});
