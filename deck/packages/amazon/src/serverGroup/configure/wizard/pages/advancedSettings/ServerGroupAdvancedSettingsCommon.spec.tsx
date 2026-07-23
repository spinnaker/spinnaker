import { shallow } from 'enzyme';
import { Field } from 'formik';
import React from 'react';

import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';

describe('ServerGroupAdvancedSettingsCommon', () => {
  it('uses the standard wizard label grid instead of centering the form controls', () => {
    const component = shallow(
      <ServerGroupAdvancedSettingsCommon app={buildApplication() as any} formik={buildFormik() as any} />,
    );

    expect(component.find('.form-group').first().find('.sm-label-right').first().hasClass('col-md-3')).toBe(true);
    expect(component.find('.form-group').first().find(Field).parent().hasClass('col-md-2')).toBe(true);
  });
});

function buildApplication() {
  return {
    attributes: {
      platformHealthOnlyShowOverride: false,
    },
  };
}

function buildFormik() {
  return {
    setFieldValue: jasmine.createSpy('setFieldValue'),
    values: {
      associatePublicIpAddress: null,
      backingData: {
        enabledMetrics: [],
        filtered: { keyPairs: [] },
        healthCheckTypes: [],
        scalingProcesses: [],
        terminationPolicies: [],
      },
      ebsOptimized: false,
      enabledMetrics: [],
      getBlockDeviceMappingsSource: jasmine.createSpy('getBlockDeviceMappingsSource').and.returnValue('default'),
      healthCheckType: 'EC2',
      instanceMonitoring: false,
      keyPair: '',
      requireIMDSv2: false,
      selectBlockDeviceMappingsSource: jasmine.createSpy('selectBlockDeviceMappingsSource'),
      suspendedProcesses: [],
      tags: {},
      terminationPolicies: [],
      toggleSuspendedProcess: jasmine.createSpy('toggleSuspendedProcess'),
      viewState: { useSimpleInstanceTypeSelector: true },
    },
  };
}
