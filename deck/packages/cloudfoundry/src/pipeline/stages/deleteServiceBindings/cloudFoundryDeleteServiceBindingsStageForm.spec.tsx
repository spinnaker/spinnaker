import { render, screen } from '@testing-library/react';
import React from 'react';

import type { Application, IStage } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';

import { CloudFoundryDeleteServiceBindingsStageConfigForm } from './CloudFoundryDeleteServiceBindingsStageConfigForm';

describe('<CloudFoundryDeleteServiceBindingsStageConfigForm/>', function () {
  const application = {
    ready: () => Promise.resolve(),
    getDataSource: () => ({ data: [] }),
  } as Application;

  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve([]));
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(Promise.resolve([]));
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
      setFieldValue: vi.fn(),
    } as any;

    const props = getProps();

    render(<CloudFoundryDeleteServiceBindingsStageConfigForm {...props} formik={formik} />);

    expect(screen.getAllByText('Target', { selector: '.label-text' })).toHaveLength(1);
    expect(screen.getAllByText('Service Instance Name', { selector: '.label-text' })).toHaveLength(2);
  });
});
