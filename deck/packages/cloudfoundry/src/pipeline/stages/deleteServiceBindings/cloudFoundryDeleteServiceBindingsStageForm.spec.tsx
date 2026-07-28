import { shallow } from 'enzyme';
import React from 'react';

import type { Application, IStage } from '@spinnaker/core';
import { AccountService, StageConfigField } from '@spinnaker/core';

import { CloudFoundryDeleteServiceBindingsStageConfigForm } from './CloudFoundryDeleteServiceBindingsStageConfigForm';

describe('<CloudFoundryDeleteServiceBindingsStageConfigForm/>', function () {
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

  it('loads component correctly with 2 serviceUnbindingRequests', function () {
    const stage = ({
      serviceUnbindingRequests: [{ serviceInstanceName: 'service1' }, { serviceInstanceName: 'service2' }],
    } as unknown) as IStage;
    const formik = {
      values: stage,
      setFieldValue: jasmine.createSpy('setFieldValue'),
    } as any;

    const props = getProps();

    const component = shallow(<CloudFoundryDeleteServiceBindingsStageConfigForm {...props} formik={formik} />);

    expect(component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Target').length).toBe(1);
    expect(
      component.find(StageConfigField).filterWhere((x) => x.prop('label') === 'Service Instance Name').length,
    ).toBe(2);
  });
});
