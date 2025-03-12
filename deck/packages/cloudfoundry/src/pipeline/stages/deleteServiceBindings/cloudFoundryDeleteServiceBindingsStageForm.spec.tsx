import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import type { IStage } from '@spinnaker/core';
import { ApplicationModelBuilder, REACT_MODULE, SpinFormik, StageConfigField } from '@spinnaker/core';
import { mockServerGroupDataSourceConfig } from '@spinnaker/mocks';

import { CloudFoundryDeleteServiceBindingsStageConfigForm } from './CloudFoundryDeleteServiceBindingsStageConfigForm';

describe('<CloudFoundryDeleteServiceBindingsStageConfigForm/>', function () {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const getProps = () => {
    return {
      application: ApplicationModelBuilder.createApplicationForTests('my-application', mockServerGroupDataSourceConfig),
      pipeline: {
        application: 'my-application',
        id: 'pipeline-id',
        limitConcurrent: true,
        keepWaitingPipelines: true,
        name: 'My Pipeline',
        parameterConfig: [],
        stages: [],
        triggers: [],
      },
    } as any;
  };

  it('loads component correctly with 2 serviceUnbindingRequests', function () {
    const stage = ({
      serviceUnbindingRequests: [{ serviceInstanceName: 'service1' }, { serviceInstanceName: 'service2' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <CloudFoundryDeleteServiceBindingsStageConfigForm {...props} formik={formik} />}
      />,
    );
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Target').length).toBe(1);
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Service Instance Name').length).toBe(2);
  });
});
