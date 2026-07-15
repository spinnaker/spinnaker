import type { Application, IStage } from 'core';
import { AccountService, StageConfigField } from 'core';
import { shallow } from 'enzyme';
import React from 'react';

import { CloudFoundryCreateServiceBindingsStageConfigForm } from './CloudFoundryCreateServiceBindingsStageConfigForm';

describe('<CloudFoundryCreateServiceBindingsStageConfigForm/>', function () {
  const application = {
    ready: () => Promise.resolve(),
    getDataSource: () => ({ data: [] }),
  } as Application;

  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([]));
  });

  const getProps = () => {
    return {
      application,
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
    const formik = {
      values: stage,
      setFieldValue: jasmine.createSpy('setFieldValue'),
    } as any;

    const props = getProps();
    const component = shallow(<CloudFoundryCreateServiceBindingsStageConfigForm {...props} formik={formik} />);

    expect(component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Target').length).toBe(1);
    expect(component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Restage Required').length).toBe(1);
    expect(component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Restart Required').length).toBe(1);
    expect(
      component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Service Instance Name').length,
    ).toBe(2);
  });
});
