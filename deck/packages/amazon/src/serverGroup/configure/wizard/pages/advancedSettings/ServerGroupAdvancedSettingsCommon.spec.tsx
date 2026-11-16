import { fireEvent, render, screen } from '@testing-library/react';
import type { FormikProps } from 'formik';
import { Form, Formik } from 'formik';
import React from 'react';

import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';
import { AWSProviderSettings } from '../../../../../aws.settings';

describe('ServerGroupAdvancedSettingsCommon', () => {
  afterEach(AWSProviderSettings.resetToOriginal);

  function renderSettings(values: Record<string, unknown> = buildValues()) {
    let formikProps: FormikProps<any>;
    render(
      <Formik initialValues={values as any} onSubmit={vi.fn()}>
        {(formik) => {
          formikProps = formik;
          return (
            <Form>
              <ServerGroupAdvancedSettingsCommon app={buildApplication() as any} formik={formik as any} />
            </Form>
          );
        }}
      </Formik>,
    );
    return () => formikProps.values;
  }

  it('uses the standard wizard label grid instead of centering the form controls', () => {
    renderSettings();

    const cooldown = screen.getByRole('textbox', { name: 'Cooldown' });
    expect(screen.getByText('Cooldown').closest('.sm-label-right')).toHaveClass('col-md-3');
    expect(cooldown.closest('.form-group')).toHaveClass('form-group');
    expect(cooldown.parentElement).toHaveClass('col-md-2');
  });

  describe('instance metadata tags', () => {
    const label = 'Allow access to instance tags from instance metadata';

    it('is hidden when launch templates are not enabled', () => {
      AWSProviderSettings.serverGroups = { ...AWSProviderSettings.serverGroups, enableLaunchTemplates: false };
      renderSettings();

      expect(screen.queryByRole('checkbox', { name: label })).not.toBeInTheDocument();
    });

    it('reflects and updates enableInstanceMetadataTags when launch templates are enabled', () => {
      AWSProviderSettings.serverGroups = { ...AWSProviderSettings.serverGroups, enableLaunchTemplates: true };
      const getValues = renderSettings({ ...buildValues(), enableInstanceMetadataTags: undefined });

      const checkbox = screen.getByRole('checkbox', { name: label });
      expect(checkbox).not.toBeChecked();

      fireEvent.click(checkbox);
      expect(checkbox).toBeChecked();
      expect(getValues().enableInstanceMetadataTags).toBe(true);

      fireEvent.click(checkbox);
      expect(checkbox).not.toBeChecked();
      expect(getValues().enableInstanceMetadataTags).toBe(false);
    });
  });
});

function buildApplication() {
  return { attributes: { platformHealthOnlyShowOverride: false } };
}

function buildValues() {
  return {
    associatePublicIpAddress: null,
    backingData: {
      enabledMetrics: [],
      filtered: { keyPairs: [] },
      healthCheckTypes: [],
      scalingProcesses: [],
      terminationPolicies: [],
    },
    cooldown: 0,
    ebsOptimized: false,
    enabledMetrics: [],
    getBlockDeviceMappingsSource: vi.fn().mockReturnValue('default'),
    healthCheckGracePeriod: 0,
    healthCheckType: 'EC2',
    instanceMonitoring: false,
    keyPair: '',
    requireIMDSv2: false,
    selectBlockDeviceMappingsSource: vi.fn(),
    suspendedProcesses: [],
    tags: {},
    terminationPolicies: [],
    toggleSuspendedProcess: vi.fn(),
    viewState: { useSimpleInstanceTypeSelector: true },
  };
}
