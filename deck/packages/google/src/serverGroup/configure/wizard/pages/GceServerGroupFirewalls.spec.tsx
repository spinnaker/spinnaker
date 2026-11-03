import type { FormikProps } from 'formik';
import type { Mocked } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';
import { GceServerGroupFirewalls } from './GceServerGroupFirewalls';

describe('GCE server group Firewalls page', () => {
  it('separates network scoped explicit and implicit firewalls and preserves unavailable selections', () => {
    const values = command({
      securityGroups: ['web-firewall', 'persisted-firewall', 'web-firewall'],
      viewState: { mode: 'clone', dirty: {}, listImplicitSecurityGroups: true },
    });
    const { formik } = testProps(values);
    render(<GceServerGroupFirewalls app={{} as any} formik={formik} />);

    expect(selectOptions()).toEqual([
      ['api-firewall', 'api-firewall (api-firewall)'],
      ['web-firewall', 'web-firewall (web-firewall)'],
      ['persisted-firewall', 'persisted-firewall (unavailable)'],
    ]);
    expect(selectedValues(screen.getByLabelText('Firewalls'))).toEqual(['web-firewall', 'persisted-firewall']);
    expect(screen.getByText('Firewalls', { selector: 'label' })).toHaveAttribute('for', 'gce-server-group-firewalls');
    expect(screen.getByText(/Show implicit firewalls/, { selector: 'label' })).toHaveAttribute(
      'for',
      'gce-show-implicit-firewalls',
    );
    expect(within(screen.getByRole('list', { name: 'Implicit firewalls' })).getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByRole('listitem')).toHaveTextContent('implicit-firewall');
  });

  it('routes explicit firewall changes through networkChanged without losing selections or tags', async () => {
    const values = command({
      securityGroups: ['persisted-firewall'],
      tags: [{ value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyCommandHandler.mockImplementation(async (nextCommand) => ({
      command: { ...nextCommand, securityGroups: [], tags: [] },
      result: { dirty: { securityGroups: ['persisted-firewall'] } },
    }));
    render(<GceServerGroupFirewalls app={{} as any} adapter={adapter} formik={formik} />);

    changeMultiSelect(screen.getByLabelText('Firewalls'), ['web-firewall', 'persisted-firewall']);

    await waitFor(() => expect(formik.setValues).toHaveBeenCalled());
    const changedCommand = adapter.applyCommandHandler.mock.lastCall[0];
    expect(changedCommand.securityGroups).toEqual(['web-firewall', 'persisted-firewall']);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'networkChanged');
    expect(formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        securityGroups: ['web-firewall', 'persisted-firewall'],
        tags: [{ value: 'existing-tag' }],
      }),
    );
  });

  it('selecting a target tag adds every matching firewall once without discarding unrelated tags', async () => {
    const values = command({
      securityGroups: ['web-firewall'],
      tags: [{ value: 'existing-tag' }, { value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    render(<GceServerGroupFirewalls app={{} as any} adapter={adapter} formik={formik} />);

    fireEvent.click(screen.getByLabelText('Target tag shared for firewall web-firewall'));

    await waitFor(() => expect(formik.setValues).toHaveBeenCalled());
    const changedCommand = adapter.applyCommandHandler.mock.lastCall[0];
    expect(changedCommand.securityGroups).toEqual(['web-firewall', 'api-firewall']);
    expect(changedCommand.tags).toEqual([{ value: 'existing-tag' }, { value: 'shared' }]);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'networkChanged');
    expect(formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        securityGroups: ['web-firewall', 'api-firewall'],
        tags: [{ value: 'existing-tag' }, { value: 'shared' }],
      }),
    );
  });

  it('removing a target tag removes firewalls associated only by that tag but keeps unavailable references', async () => {
    const values = command({
      securityGroups: ['web-firewall', 'api-firewall', 'persisted-firewall'],
      tags: [{ value: 'existing-tag' }, { value: 'shared' }],
    });
    const { adapter, formik } = testProps(values);
    render(<GceServerGroupFirewalls app={{} as any} adapter={adapter} formik={formik} />);

    fireEvent.click(screen.getByLabelText('Target tag shared for firewall web-firewall'));

    await waitFor(() => expect(formik.setValues).toHaveBeenCalled());
    const changedCommand = adapter.applyCommandHandler.mock.lastCall[0];
    expect(changedCommand.securityGroups).toEqual(['persisted-firewall']);
    expect(changedCommand.tags).toEqual([{ value: 'existing-tag' }]);
  });

  it('refreshes firewall data while preserving explicit selections and target tags', async () => {
    const values = command({
      securityGroups: ['persisted-firewall'],
      tags: [{ value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationRefresh.mockResolvedValue({
      command: {
        ...values,
        backingData: { ...values.backingData, refreshed: true },
        securityGroups: [],
        tags: [],
      },
      result: { dirty: {} },
    });
    render(<GceServerGroupFirewalls app={{} as any} adapter={adapter} formik={formik} />);

    fireEvent.click(screen.getByRole('button', { name: 'Refresh firewalls' }));

    await waitFor(() => expect(formik.setValues).toHaveBeenCalled());
    expect(adapter.applyConfigurationRefresh).toHaveBeenCalledWith(values, 'refreshSecurityGroups');
    expect(formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        backingData: expect.objectContaining({ refreshed: true }),
        securityGroups: ['persisted-firewall'],
        tags: [{ value: 'existing-tag' }],
      }),
    );
  });
});

function selectOptions(): string[][] {
  return within(screen.getByLabelText('Firewalls'))
    .getAllByRole('option')
    .map((option) => [(option as HTMLOptionElement).value, option.textContent || '']);
}

function selectedValues(select: HTMLElement): string[] {
  return Array.from((select as HTMLSelectElement).selectedOptions).map((option) => option.value);
}

function changeMultiSelect(select: HTMLElement, values: string[]): void {
  Array.from((select as HTMLSelectElement).options).forEach((option) => {
    option.selected = values.includes(option.value);
  });
  fireEvent.change(select);
}

function testProps(values = command()) {
  const formik = ({
    values,
    setFieldValue: vi.fn(),
    setValues: vi.fn(),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: vi.fn().mockImplementation(async (nextCommand: IGceServerGroupCommand) => ({
      command: nextCommand,
      result: { dirty: {} },
    })),
    applyConfigurationRefresh: vi.fn().mockImplementation(async (nextCommand: IGceServerGroupCommand) => ({
      command: nextCommand,
      result: { dirty: {} },
    })),
  } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account-a',
    regional: false,
    region: 'us-central1',
    network: 'default',
    securityGroups: [],
    tags: [],
    backingData: {
      filtered: {},
      securityGroups: {
        'account-a': {
          gce: {
            global: [
              { id: 'web-firewall', name: 'web-firewall', network: 'default', targetTags: '[web, shared]' },
              { id: 'api-firewall', name: 'api-firewall', network: 'default', targetTags: ['api', 'shared'] },
              { id: 'implicit-firewall', name: 'implicit-firewall', network: 'default', targetTags: [] },
              { id: 'other-network-firewall', name: 'other-network-firewall', network: 'other', targetTags: ['web'] },
              { id: 'web-firewall', name: 'web-firewall', network: 'default', targetTags: ['web'] },
            ],
          },
        },
        'account-b': {
          gce: {
            global: [{ id: 'other-account-firewall', network: 'default', targetTags: ['web'] }],
          },
        },
      },
    },
    viewState: { mode: 'create', dirty: {} },
    ...overrides,
  };
}
