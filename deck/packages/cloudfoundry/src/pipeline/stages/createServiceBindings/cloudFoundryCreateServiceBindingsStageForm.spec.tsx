import { render, screen } from '@testing-library/react';
import type { Application, IStage } from 'core';
import { AccountService } from 'core';
import React from 'react';

import { CloudFoundryCreateServiceBindingsStageConfigForm } from './CloudFoundryCreateServiceBindingsStageConfigForm';

describe('<CloudFoundryCreateServiceBindingsStageConfigForm/>', function () {
  const application = {
    ready: () => Promise.resolve(),
    getDataSource: () => ({ data: [] }),
  } as Application;

  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve([]));
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(Promise.resolve([]));
    vi.spyOn(AccountService, 'getArtifactAccounts').mockReturnValue(Promise.resolve([]));
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
      setFieldValue: vi.fn(),
    } as any;

    const props = getProps();
    render(<CloudFoundryCreateServiceBindingsStageConfigForm {...props} formik={formik} />);

    expect(screen.getAllByText('Target', { selector: '.label-text' })).toHaveLength(1);
    expect(screen.getAllByText('Restage Required', { selector: '.label-text' })).toHaveLength(1);
    expect(screen.getAllByText('Restart Required', { selector: '.label-text' })).toHaveLength(1);
    expect(screen.getAllByText('Service Instance Name', { selector: '.label-text' })).toHaveLength(2);
  });
});
