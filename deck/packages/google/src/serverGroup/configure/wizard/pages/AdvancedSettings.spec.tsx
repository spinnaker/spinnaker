import { shallow } from 'enzyme';
import React from 'react';

import { MapEditor } from '@spinnaker/core';

import { AdvancedSettings } from './AdvancedSettings';
import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';

describe('GCE server group Advanced Settings page', () => {
  it('restores persisted advanced fields and preserves unknown options and maps', () => {
    const values = command();
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    expect(wrapper.find('[data-testid="minimum-cpu-platform"] option').map((option) => option.prop('value'))).toEqual([
      'Automatic',
      'legacy-platform',
    ]);
    expect(wrapper.find('[data-testid="disk-type-0"] option').map((option) => option.prop('value'))).toEqual([
      'pd-ssd',
      'legacy-disk',
    ]);
    expect(wrapper.find('[data-testid="accelerator-type-0"] option').map((option) => option.prop('value'))).toEqual([
      'nvidia-tesla-t4',
      'legacy-accelerator',
    ]);
    expect(wrapper.find('[data-testid="service-account"] input').prop('value')).toBe('custom@example.com');
    expect(wrapper.find('[data-testid="auth-scope-0"] input').prop('value')).toBe('unknown.scope');
    expect(wrapper.find('[data-testid="enable-confidential-compute"] input').prop('checked')).toBe(true);

    const editors = wrapper.find(MapEditor);
    expect(editors.at(0).prop('model')).toBe(values.instanceMetadata);
    expect(editors.at(1).prop('model')).toBe(values.labels);
    expect(editors.at(2).prop('model')).toBe(values.resourceManagerTags);
  });

  it('preserves unknown disk and accelerator fields while editing known values', () => {
    const values = command();
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    wrapper.find('[data-testid="disk-size-0"]').simulate('change', { target: { value: '200' } });
    wrapper.find('[data-testid="accelerator-count-0"]').simulate('change', { target: { value: '4' } });

    expect(values.disks[0]).toEqual({ type: 'legacy-disk', sizeGb: 200, sourceImage: 'image', unknown: 'keep' });
    expect(values.acceleratorConfigs[0]).toEqual({
      acceleratorType: 'legacy-accelerator',
      acceleratorCount: 4,
      unknown: 'keep',
    });
  });

  it('edits local SSD count separately while preserving existing local SSD configuration', () => {
    const firstLocalSsd = { type: 'local-ssd', sizeGb: 375, autoDelete: true, unknown: 'first' };
    const secondLocalSsd = { type: 'local-ssd', sizeGb: 375, autoDelete: true, unknown: 'second' };
    const values = command({ disks: [command().disks[0], firstLocalSsd, secondLocalSsd] });
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    expect(wrapper.find('[data-testid="local-ssd-count"]').prop('value')).toBe(2);
    expect(wrapper.find('[data-testid^="disk-type-"]').length).toBe(1);

    wrapper.find('[data-testid="local-ssd-count"]').simulate('change', { target: { value: '3' } });
    expect(values.disks.slice(1, 3)).toEqual([firstLocalSsd, secondLocalSsd]);
    expect(values.disks[3]).toEqual({ type: 'local-ssd', sizeGb: 375 });

    wrapper.find('[data-testid="local-ssd-count"]').simulate('change', { target: { value: '1' } });
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
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    expect(wrapper.find('[data-testid="disk-size-0"]').prop('type')).toBe('text');
    expect(wrapper.find('[data-testid="accelerator-count-0"]').prop('type')).toBe('text');
    wrapper.find('[data-testid="disk-size-0"]').simulate('change', { target: { value: '${ diskSize }' } });
    wrapper
      .find('[data-testid="accelerator-count-0"]')
      .simulate('change', { target: { value: '${ acceleratorCount }' } });

    expect(values.disks[0].sizeGb).toBe('${ diskSize }');
    expect(values.acceleratorConfigs[0].acceleratorCount).toBe('${ acceleratorCount }');
    expect(new AdvancedSettings({ app: {} as any, formik: formik(values) } as any).validate(values)).toEqual({});
  });

  it('enforces concrete disk and accelerator bounds and rejects expressions outside pipelines', () => {
    const page = new AdvancedSettings({ app: {} as any, formik: formik(command()) } as any);
    const invalid = command({
      disks: [{ type: 'pd-ssd', sizeGb: 9 }],
      acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 0 }],
    });

    expect(page.validate(invalid)).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
    });
    expect(
      page.validate(
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
      page.validate(
        command({
          disks: [{ type: 'pd-ssd', sizeGb: '${ diskSize }' }],
          acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: '${ count }' }],
        }),
      ),
    ).toEqual({
      disks: 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.',
      acceleratorConfigs: 'Every accelerator requires a type and a supported positive integer count.',
    });

    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(command())} />);
    expect(wrapper.find('[data-testid="disk-size-0"]').prop('min')).toBe(10);
    expect(wrapper.find('[data-testid="disk-size-0"]').prop('max')).toBe(65536);
  });

  it('preserves and updates nested partner metadata without flattening unknown data', () => {
    const values = command({ partnerMetadata: { partner: { entries: { unknown: 'keep' } } } });
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    expect(wrapper.find('[data-testid="partner-metadata"]').prop('value')).toBe(
      JSON.stringify(values.partnerMetadata, null, 2),
    );
    wrapper.find('[data-testid="partner-metadata"]').simulate('change', {
      target: { value: '{"partner":{"entries":{"unknown":"keep","added":"value"}}}' },
    });

    expect(values.partnerMetadata).toEqual({
      partner: { entries: { unknown: 'keep', added: 'value' } },
    });
  });

  it('keeps scheduling and shielded VM constraints internally consistent', () => {
    const values = command({
      preemptible: false,
      automaticRestart: true,
      onHostMaintenance: 'MIGRATE',
      enableVtpm: true,
      enableIntegrityMonitoring: true,
    });
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    wrapper.find('[data-testid="preemptible-on"]').simulate('change');
    expect(values.preemptible).toBe(true);
    expect(values.automaticRestart).toBe(false);
    expect(values.onHostMaintenance).toBe('TERMINATE');

    wrapper.find('[data-testid="preemptible-off"]').simulate('change');
    expect(values.preemptible).toBe(false);
    expect(values.automaticRestart).toBe(true);
    expect(values.onHostMaintenance).toBe('MIGRATE');

    wrapper.find('[data-testid="enable-vtpm"]').simulate('change', { target: { checked: false } });
    expect(values.enableVtpm).toBe(false);
    expect(values.enableIntegrityMonitoring).toBe(false);
  });

  it('uses accessible non-submit controls for every add and remove action', () => {
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(command())} />);
    const actionButtons = wrapper.find('button');

    expect(actionButtons.length).toBeGreaterThan(0);
    actionButtons.forEach((button) => {
      expect(button.prop('type')).toBe('button');
      expect(button.prop('aria-label') || button.text().trim()).toBeTruthy();
    });
  });

  it('adds and removes disks, accelerators, tags, and custom scopes without mutating existing entries', () => {
    const values = command();
    const originalDisk = values.disks[0];
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

    wrapper.find('[data-testid="add-disk"]').simulate('click');
    wrapper.find('[data-testid="add-accelerator"]').simulate('click');
    wrapper.find('[data-testid="add-network-tag"]').simulate('click');
    wrapper.find('[data-testid="add-auth-scope"]').simulate('click');

    expect(values.disks.length).toBe(2);
    expect(values.disks[0]).toBe(originalDisk);
    expect(values.acceleratorConfigs.length).toBe(2);
    expect(values.tags).toEqual([{ value: 'existing-tag', unknown: 'keep' }, { value: '' }]);
    expect(values.authScopes).toEqual(['unknown.scope', '']);

    wrapper.find('[data-testid="remove-disk-0"]').simulate('click');
    wrapper.find('[data-testid="remove-accelerator-0"]').simulate('click');
    wrapper.find('[data-testid="remove-network-tag-0"]').simulate('click');
    wrapper.find('[data-testid="remove-auth-scope-0"]').simulate('click');

    expect(values.disks.length).toBe(1);
    expect(values.acceleratorConfigs.length).toBe(1);
    expect(values.tags).toEqual([{ value: '' }]);
    expect(values.authScopes).toEqual(['']);
  });

  it('validates invalid disks, accelerators, maps, tags, scopes, and confidential settings', () => {
    const page = new AdvancedSettings({ app: {} as any, formik: formik(command()) } as any);

    expect(
      page.validate(
        command({
          disks: [{ type: '', sizeGb: 0 }],
          acceleratorConfigs: [{ acceleratorType: '', acceleratorCount: 0 }],
          instanceMetadata: { '': 'value' },
          labels: { label: '' },
          resourceManagerTags: { tag: '' },
          tags: [{ value: '' }],
          authScopes: [''],
          partnerMetadata: '{invalid',
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
      partnerMetadata: 'Partner metadata must be a JSON object.',
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
      partnerMetadata: '{invalid',
      enableConfidentialCompute: true,
      confidentialInstanceType: '',
    });
    const wrapper = shallow(<AdvancedSettings app={{} as any} formik={formik(values)} />);

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
      ['[data-testid="partner-metadata"]', 'gce-advanced-partner-metadata-error'],
      ['#gce-confidential-instance-type', 'gce-advanced-confidential-instance-type-error'],
      ['[aria-label="Auth scope 1"]', 'gce-advanced-auth-scopes-error'],
    ].forEach(([selector, errorId]) => {
      const control = wrapper.find(selector);
      expect(control.prop('aria-invalid')).withContext(selector).toBe(true);
      expect(control.prop('aria-describedby')).withContext(selector).toBe(errorId);
    });

    expectAdvancedError(wrapper, 'gce-advanced-disks-error', 'Every persistent disk requires a type');
    expectAdvancedError(wrapper, 'gce-advanced-accelerators-error', 'Every accelerator requires a type');
    expectAdvancedError(wrapper, 'gce-advanced-instance-metadata-error', 'Metadata keys cannot be empty.');
    expectAdvancedError(wrapper, 'gce-advanced-labels-error', 'Label values cannot be empty.');
    expectAdvancedError(
      wrapper,
      'gce-advanced-resource-manager-tags-error',
      'Resource Manager tag values cannot be empty.',
    );
    expectAdvancedError(wrapper, 'gce-advanced-network-tags-error', 'Network tags cannot be empty.');
    expectAdvancedError(wrapper, 'gce-advanced-partner-metadata-error', 'Partner metadata must be a JSON object.');
    expectAdvancedError(
      wrapper,
      'gce-advanced-confidential-instance-type-error',
      'Confidential instance type required.',
    );
    expectAdvancedError(wrapper, 'gce-advanced-auth-scopes-error', 'Auth scopes cannot be empty.');
  });
});

function expectAdvancedError(wrapper: ReturnType<typeof shallow>, id: string, message: string): void {
  const error = wrapper.find(`#${id}`);
  expect(error.prop('role')).withContext(id).toBe('alert');
  expect(error.text()).withContext(id).toContain(message);
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account',
    regional: false,
    viewState: {
      mode: 'clone',
      instanceTypeDetails: { storage: { localSSDSupported: true } },
      acceleratorTypes: [
        {
          name: 'nvidia-tesla-t4',
          description: 'NVIDIA Tesla T4',
          availableCardCounts: [1, 2, 4],
        },
      ],
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
    setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
      values[field] = value;
    }),
  };
}
