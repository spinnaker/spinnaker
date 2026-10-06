import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { IApplicationAttributes } from '../service/ApplicationWriter';
import { ApplicationProviderFields } from './ApplicationProviderFields';
import { CloudProviderRegistry } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';

describe('ApplicationProviderFields', () => {
  const firstProvider = 'applicationFieldsFirst';
  const secondProvider = 'applicationFieldsSecond';

  beforeEach(() => {
    SETTINGS.providers[firstProvider] = { enabledByDefault: true };
    SETTINGS.providers[secondProvider] = { associateAddress: false };
    CloudProviderRegistry.registerProvider(firstProvider, {
      name: 'First',
      applicationProviderFields: [{ field: 'enabledByDefault', label: 'Enabled by default', type: 'boolean' }],
    });
    CloudProviderRegistry.registerProvider(secondProvider, {
      name: 'Second',
      applicationProviderFields: [
        {
          field: 'associateAddress',
          helpKey: 'second.associateAddress',
          label: 'Associate address',
          type: 'boolean',
        },
      ],
    });
  });

  afterEach(() => {
    delete SETTINGS.providers[firstProvider];
    delete SETTINGS.providers[secondProvider];
    (CloudProviderRegistry as any).providers.delete(firstProvider);
    (CloudProviderRegistry as any).providers.delete(secondProvider);
  });

  it('uses selected unique providers when providers are selected', () => {
    const { container } = render(
      <ApplicationProviderFields
        application={{ name: 'app' }}
        availableProviders={[firstProvider, secondProvider]}
        selectedProviders={[secondProvider, secondProvider]}
        onChange={() => undefined}
      />,
    );

    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    expect(container.querySelector('input')).toHaveAttribute('data-provider', secondProvider);
  });

  it('uses all unique available providers when none are selected', () => {
    render(
      <ApplicationProviderFields
        application={{ name: 'app' }}
        availableProviders={[firstProvider, firstProvider, secondProvider]}
        selectedProviders={[]}
        onChange={() => undefined}
      />,
    );

    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
  });

  it('clones the application and initializes configured defaults only when values are absent', () => {
    const application: IApplicationAttributes = {
      name: 'app',
      providerSettings: { [secondProvider]: { associateAddress: true } },
    };
    const onChange = vi.fn();

    render(
      <ApplicationProviderFields
        application={application}
        availableProviders={[firstProvider, secondProvider]}
        selectedProviders={[]}
        onChange={onChange}
      />,
    );

    expect(application.providerSettings[firstProvider]).toBeUndefined();
    expect(onChange).toHaveBeenCalledTimes(1);
    const changed = onChange.mock.lastCall[0];
    expect(changed).not.toBe(application);
    expect(changed.providerSettings[firstProvider].enabledByDefault).toBe(true);
    expect(changed.providerSettings[secondProvider].associateAddress).toBe(true);
  });

  it('writes checkbox values to the nested provider setting on a clone', () => {
    const application: IApplicationAttributes = {
      name: 'app',
      providerSettings: { [firstProvider]: { enabledByDefault: true } },
    };
    const onChange = vi.fn();
    render(
      <ApplicationProviderFields
        application={application}
        availableProviders={[firstProvider]}
        selectedProviders={[firstProvider]}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole('checkbox', { name: /enabled by default/i }));

    const changed = onChange.mock.lastCall[0];
    expect(changed).not.toBe(application);
    expect(changed.providerSettings[firstProvider].enabledByDefault).toBe(false);
    expect(application.providerSettings[firstProvider].enabledByDefault).toBe(true);
  });

  it('renders provider help metadata with its boolean field', () => {
    const { container } = render(
      <ApplicationProviderFields
        application={{ name: 'app', providerSettings: { [secondProvider]: { associateAddress: false } } }}
        availableProviders={[secondProvider]}
        selectedProviders={[secondProvider]}
        onChange={() => undefined}
      />,
    );

    expect(screen.getByText(/associate address/i)).toBeInTheDocument();
    expect(container.querySelector('.help-field')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /associate address/i })).not.toBeChecked();
  });
});
