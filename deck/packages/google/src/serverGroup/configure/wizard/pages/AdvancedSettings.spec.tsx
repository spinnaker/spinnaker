import { fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';

import { AdvancedSettings, validateGceAdvancedSettings } from './AdvancedSettings';
import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';

describe('GCE server group Advanced Settings page', () => {
  it('restores persisted advanced fields and preserves unknown options and maps', () => {
    const values = command();
    renderAdvanced(values);

    expect(optionValues(screen.getByTestId('minimum-cpu-platform'))).toEqual(['Automatic', 'legacy-platform']);
    expect(optionValues(screen.getByTestId('disk-type-0'))).toEqual(['pd-ssd', 'legacy-disk']);
    expect(optionValues(screen.getByTestId('accelerator-type-0'))).toEqual(['nvidia-tesla-t4', 'legacy-accelerator']);
    expect(screen.getByTestId('service-account').querySelector('input')).toHaveValue('custom@example.com');
    expect(screen.getByTestId('auth-scope-0').querySelector('input')).toHaveValue('unknown.scope');
    expect(screen.getByTestId('enable-confidential-compute').querySelector('input')).toBeChecked();
    expect(mapValues('Custom Metadata')).toEqual(['unknownMetadata', 'keep']);
    expect(mapValues('Labels')).toEqual(['unknownLabel', 'keep']);
    expect(mapValues('Resource Manager Tags')).toEqual(['unknownTag', 'keep']);
  });

  it('preserves unknown disk and accelerator fields while editing known values', () => {
    // A persisted legacy accelerator only offers its stored count, so edit a known type with selectable counts.
    const values = command({
      acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 2, unknown: 'keep' }],
    });
    renderAdvanced(values);

    fireEvent.change(screen.getByTestId('disk-size-0'), { target: { value: '200' } });
    fireEvent.change(screen.getByTestId('accelerator-count-0'), { target: { value: '4' } });

    expect(values.disks[0]).toEqual({ type: 'legacy-disk', sizeGb: 200, sourceImage: 'image', unknown: 'keep' });
    expect(values.acceleratorConfigs[0]).toEqual({
      acceleratorType: 'nvidia-tesla-t4',
      acceleratorCount: 4,
      unknown: 'keep',
    });
  });

  it('edits local SSD count separately while preserving existing local SSD configuration', () => {
    const firstLocalSsd = { type: 'local-ssd', sizeGb: 375, autoDelete: true, unknown: 'first' };
    const secondLocalSsd = { type: 'local-ssd', sizeGb: 375, autoDelete: true, unknown: 'second' };
    const values = command({ disks: [command().disks[0], firstLocalSsd, secondLocalSsd] });
    renderAdvanced(values);

    expect(screen.getByTestId('local-ssd-count')).toHaveValue(2);
    expect(screen.getAllByTestId(/^disk-type-/)).toHaveLength(1);

    fireEvent.change(screen.getByTestId('local-ssd-count'), { target: { value: '3' } });
    expect(values.disks.slice(1, 3)).toEqual([firstLocalSsd, secondLocalSsd]);
    expect(values.disks[3]).toEqual({ type: 'local-ssd', sizeGb: 375 });

    fireEvent.change(screen.getByTestId('local-ssd-count'), { target: { value: '1' } });
    expect(values.disks).toEqual([command().disks[0], firstLocalSsd]);
  });

  it('preserves pipeline expressions for disk sizes and accelerator counts', () => {
    const values = command({
      viewState: { ...command().viewState, mode: 'editPipeline' },
      disks: [{ type: 'pd-ssd', sizeGb: '${ parameters.diskSize }' }],
      acceleratorConfigs: [
        { acceleratorType: 'nvidia-tesla-t4', acceleratorCount: '${ parameters.acceleratorCount }' },
      ],
    });
    renderAdvanced(values);

    expect(screen.getByTestId('disk-size-0')).toHaveAttribute('type', 'text');
    expect(screen.getByTestId('accelerator-count-0')).toHaveAttribute('type', 'text');
    fireEvent.change(screen.getByTestId('disk-size-0'), { target: { value: '${ diskSize }' } });
    fireEvent.change(screen.getByTestId('accelerator-count-0'), { target: { value: '${ acceleratorCount }' } });

    expect(values.disks[0].sizeGb).toBe('${ diskSize }');
    expect(values.acceleratorConfigs[0].acceleratorCount).toBe('${ acceleratorCount }');
    expect(validateGceAdvancedSettings(values)).toEqual({});
  });

  it('enforces concrete disk and accelerator bounds and rejects expressions outside pipelines', () => {
    const invalid = command({
      disks: [{ type: 'pd-ssd', sizeGb: 9 }],
      acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 0 }],
    });

    expect(validateGceAdvancedSettings(invalid)).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
    });
    expect(
      validateGceAdvancedSettings(
        command({
          disks: [{ type: 'pd-ssd', sizeGb: 65537 }],
          acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 3 }],
        }),
      ),
    ).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
    });
    expect(
      validateGceAdvancedSettings(
        command({
          disks: [{ type: 'pd-ssd', sizeGb: '${ diskSize }' }],
          acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: '${ count }' }],
        }),
      ),
    ).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
    });

    renderAdvanced(command());
    expect(screen.getByTestId('disk-size-0')).toHaveAttribute('min', '10');
    expect(screen.getByTestId('disk-size-0')).toHaveAttribute('max', '65536');
  });

  it('does not render or validate the unsupported partner metadata editor', () => {
    const values = command({ partnerMetadata: { partner: { entries: { unknown: 'keep' } } } });
    const { container } = renderAdvanced(values);

    expect(screen.queryByTestId('partner-metadata')).not.toBeInTheDocument();
    expect(container).not.toHaveTextContent('Partner Metadata');
    expect(validateGceAdvancedSettings(command({ partnerMetadata: '{legacy-invalid-json' }))).toEqual({});
  });

  it('keeps scheduling and shielded VM constraints internally consistent', () => {
    const values = command({
      preemptible: false,
      automaticRestart: true,
      onHostMaintenance: 'MIGRATE',
      enableVtpm: true,
      enableIntegrityMonitoring: true,
    });
    const page = renderAdvanced(values);

    fireEvent.click(screen.getByTestId('preemptible-on'));
    expect(values.preemptible).toBe(true);
    expect(values.automaticRestart).toBe(false);
    expect(values.onHostMaintenance).toBe('TERMINATE');

    page.rerenderPage(values);
    fireEvent.click(screen.getByTestId('preemptible-off'));
    expect(values.preemptible).toBe(false);
    expect(values.automaticRestart).toBe(true);
    expect(values.onHostMaintenance).toBe('MIGRATE');

    fireEvent.click(screen.getByTestId('enable-vtpm'));
    expect(values.enableVtpm).toBe(false);
    expect(values.enableIntegrityMonitoring).toBe(false);
  });

  it('uses accessible non-submit controls for every add and remove action', () => {
    const { container } = renderAdvanced(command());
    const actionButtons = Array.from(container.querySelectorAll('button'));

    expect(actionButtons.length).toBeGreaterThan(0);
    actionButtons.forEach((button) => {
      expect(button).toHaveAttribute('type', 'button');
      expect(button.getAttribute('aria-label') || button.textContent?.trim()).toBeTruthy();
    });
  });

  it('adds and removes disks, accelerators, tags, and custom scopes without mutating existing entries', () => {
    const values = command();
    const originalDisk = values.disks[0];
    renderAdvanced(values);

    fireEvent.click(screen.getByTestId('add-disk'));
    fireEvent.click(screen.getByTestId('add-accelerator'));
    fireEvent.click(screen.getByTestId('add-network-tag'));
    fireEvent.click(screen.getByTestId('add-auth-scope'));

    expect(values.disks.length).toBe(2);
    expect(values.disks[0]).toBe(originalDisk);
    expect(values.acceleratorConfigs.length).toBe(2);
    expect(values.tags).toEqual([{ value: 'existing-tag', unknown: 'keep' }, { value: '' }]);
    expect(values.authScopes).toEqual(['unknown.scope', '']);

    fireEvent.click(screen.getByTestId('remove-disk-0'));
    fireEvent.click(screen.getByTestId('remove-accelerator-0'));
    fireEvent.click(screen.getByTestId('remove-network-tag-0'));
    fireEvent.click(screen.getByTestId('remove-auth-scope-0'));

    expect(values.disks.length).toBe(1);
    expect(values.acceleratorConfigs.length).toBe(1);
    expect(values.tags).toEqual([{ value: '' }]);
    expect(values.authScopes).toEqual(['']);
  });

  it('validates invalid disks, accelerators, maps, tags, scopes, and confidential settings', () => {
    expect(
      validateGceAdvancedSettings(
        command({
          disks: [{ type: '', sizeGb: 0 }],
          acceleratorConfigs: [{ acceleratorType: '', acceleratorCount: 0 }],
          instanceMetadata: { '': 'value' },
          labels: { label: '' },
          resourceManagerTags: { tag: '' },
          tags: [{ value: '' }],
          authScopes: [''],
          enableConfidentialCompute: true,
          confidentialInstanceType: '',
        }),
      ),
    ).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
      instanceMetadata: 'Metadata keys cannot be empty.',
      labels: 'Label values cannot be empty.',
      resourceManagerTags: 'Resource Manager tag values cannot be empty.',
      tags: 'Network tags cannot be empty.',
      authScopes: 'Auth scopes cannot be empty.',
      confidentialInstanceType: 'Confidential instance type required.',
    });
  });

  it('renders every page-owned validation error and associates it with its controls', () => {
    const values = command({
      disks: [{ type: '', sizeGb: 0 }],
      acceleratorConfigs: [{ acceleratorType: '', acceleratorCount: 0 }],
      instanceMetadata: { '': 'value' },
      labels: { label: '' },
      resourceManagerTags: { tag: '' },
      tags: [{ value: '' }],
      authScopes: [''],
      enableConfidentialCompute: true,
      confidentialInstanceType: '',
    });
    const { container } = renderAdvanced(values);

    [
      ['[data-testid="local-ssd-count"]', 'gce-advanced-disks-error'],
      ['[data-testid="disk-type-0"]', 'gce-advanced-disks-error'],
      ['[data-testid="disk-size-0"]', 'gce-advanced-disks-error'],
      ['[data-testid="accelerator-type-0"]', 'gce-advanced-accelerators-error'],
      ['[data-testid="accelerator-count-0"]', 'gce-advanced-accelerators-error'],
      ['[aria-label="Custom Metadata"]', 'gce-advanced-instance-metadata-error'],
      ['[aria-label="Labels"]', 'gce-advanced-labels-error'],
      ['[aria-label="Resource Manager Tags"]', 'gce-advanced-resource-manager-tags-error'],
      ['[aria-label="Network tag 1"]', 'gce-advanced-network-tags-error'],
      ['#gce-confidential-instance-type', 'gce-advanced-confidential-instance-type-error'],
      ['[aria-label="Auth scope 1"]', 'gce-advanced-auth-scopes-error'],
    ].forEach(([selector, errorId]) => {
      const control = container.querySelector(selector);
      expect(control, selector).toHaveAttribute('aria-invalid', 'true');
      expect(control, selector).toHaveAttribute('aria-describedby', errorId);
    });

    expectAdvancedError('gce-advanced-disks-error', 'Every persistent disk requires a type');
    expectAdvancedError('gce-advanced-accelerators-error', 'Every accelerator requires a type');
    expectAdvancedError('gce-advanced-instance-metadata-error', 'Metadata keys cannot be empty.');
    expectAdvancedError('gce-advanced-labels-error', 'Label values cannot be empty.');
    expectAdvancedError('gce-advanced-resource-manager-tags-error', 'Resource Manager tag values cannot be empty.');
    expectAdvancedError('gce-advanced-network-tags-error', 'Network tags cannot be empty.');
    expectAdvancedError('gce-advanced-confidential-instance-type-error', 'Confidential instance type required.');
    expectAdvancedError('gce-advanced-auth-scopes-error', 'Auth scopes cannot be empty.');
  });
});

function renderAdvanced(values: IGceServerGroupCommand) {
  const pageProps = { app: {} as any, formik: formik(values) };
  const rendered = render(<AdvancedSettings {...pageProps} />);
  return {
    ...rendered,
    rerenderPage(nextValues: IGceServerGroupCommand) {
      rendered.rerender(<AdvancedSettings {...pageProps} formik={formik(nextValues)} />);
    },
  };
}

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}

function mapValues(label: string): string[] {
  const editor = document.querySelector(`[aria-label="${label}"]`)!;
  return Array.from(editor.querySelectorAll('input')).map((input) => input.value);
}

function expectAdvancedError(id: string, message: string): void {
  const error = document.getElementById(id);
  expect(error, id).toHaveAttribute('role', 'alert');
  expect(error, id).toHaveTextContent(message);
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account',
    regional: false,
    viewState: {
      mode: 'clone',
      instanceTypeDetails: { storage: { localSSDSupported: true } },
      acceleratorTypes: [{ name: 'nvidia-tesla-t4', description: 'NVIDIA Tesla T4', availableCardCounts: [1, 2, 4] }],
    },
    backingData: {
      persistentDiskTypes: ['pd-ssd'],
      authScopes: ['compute.readonly'],
      filtered: { cpuPlatforms: ['Automatic'] },
    },
    minCpuPlatform: 'legacy-platform',
    disks: [{ type: 'legacy-disk', sizeGb: 100, sourceImage: 'image', unknown: 'keep' }],
    acceleratorConfigs: [{ acceleratorType: 'legacy-accelerator', acceleratorCount: 2, unknown: 'keep' }],
    userData: 'startup data',
    instanceMetadata: { unknownMetadata: 'keep' },
    labels: { unknownLabel: 'keep' },
    resourceManagerTags: { unknownTag: 'keep' },
    tags: [{ value: 'existing-tag', unknown: 'keep' }],
    serviceAccountEmail: 'custom@example.com',
    authScopes: ['unknown.scope'],
    associatePublicIpAddress: false,
    canIpForward: true,
    enableSecureBoot: true,
    enableVtpm: true,
    enableIntegrityMonitoring: true,
    enableConfidentialCompute: true,
    confidentialInstanceType: 'SEV',
    preemptible: false,
    automaticRestart: true,
    onHostMaintenance: 'MIGRATE',
    ...overrides,
  } as IGceServerGroupCommand;
}

function formik(values: IGceServerGroupCommand): any {
  return {
    values,
    setFieldValue: vi.fn().mockImplementation((field: string, value: any) => {
      values[field] = value;
    }),
  };
}
