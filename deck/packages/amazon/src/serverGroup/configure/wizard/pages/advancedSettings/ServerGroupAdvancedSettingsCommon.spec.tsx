import { render, screen } from '@testing-library/react';
import { Form, Formik } from 'formik';
import React from 'react';

import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';

describe('ServerGroupAdvancedSettingsCommon', () => {
  it('uses the standard wizard label grid instead of centering the form controls', () => {
    render(
      <Formik initialValues={buildValues() as any} onSubmit={vi.fn()}>
        {(formik) => (
          <Form>
            <ServerGroupAdvancedSettingsCommon app={buildApplication() as any} formik={formik as any} />
          </Form>
        )}
      </Formik>,
    );

    const cooldown = screen.getByRole('textbox', { name: 'Cooldown' });
    expect(screen.getByText('Cooldown').closest('.sm-label-right')).toHaveClass('col-md-3');
    expect(cooldown.closest('.form-group')).toHaveClass('form-group');
    expect(cooldown.parentElement).toHaveClass('col-md-2');
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
