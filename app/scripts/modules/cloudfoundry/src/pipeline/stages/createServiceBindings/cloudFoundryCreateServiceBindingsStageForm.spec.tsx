import React from 'react';
import { mock } from 'angular';
import { mount } from 'enzyme';

import { ApplicationModelBuilder, IStage, REACT_MODULE, SpinFormik, StageConfigField } from 'core';
import { CloudFoundryCreateServiceBindingsStageConfigForm } from './CloudFoundryCreateServiceBindingsStageConfigForm';

describe('<CloudFoundryCreateServiceBindingsStageConfigForm/>', function () {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const getProps = () => {
    return {
      application: ApplicationModelBuilder.createApplicationForTests('my-application'),
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

  it('loads component correctly with 2 serviceBindingRequests', function () {
    const stage = ({
      serviceBindingRequests: [{ serviceInstanceName: 'service1' }, { serviceInstanceName: 'service2' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <CloudFoundryCreateServiceBindingsStageConfigForm {...props} formik={formik} />}
      />,
    );
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Target').length).toBe(1);
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Restage Required').length).toBe(1);
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Restart Required').length).toBe(1);
    expect(component.find(StageConfigField).findWhere((x) => x.text() === 'Service Instance Name').length).toBe(2);
  });
});
