import { mock, noop } from 'angular';
import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import { set } from 'lodash';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { IExecutionsProps, IExecutionsState } from './Executions';
import { Executions } from './Executions';
import type { Application } from '../../application';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { ViewStateCache } from '../../cache';
import { INSIGHT_FILTER_STATE_MODEL } from '../../insight/insightFilterState.model';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry';
import { REACT_MODULE } from '../../reactShims';
import * as State from '../../state';
import { Spinner } from '../../widgets/spinners/Spinner';

describe('<Executions/>', () => {
  let component: ReactWrapper<IExecutionsProps, IExecutionsState>;
  let application: Application;

  async function settleInitialization() {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    act(() => jasmine.clock().tick(50));
    component.update();
  }

  function initializeApplication(data?: any) {
    set(application, 'executions.activate', noop);
    set(application, 'pipelineConfigs.activate', noop);
    if (data && 'executions' in data) {
      application.executions.data = data.executions;
      application.executions.loaded = true;
    }
    if (data && 'pipelineConfigs' in data) {
      application.pipelineConfigs.data = data.pipelineConfigs;
      application.pipelineConfigs.loaded = true;
    }

    component = mount(<Executions app={application} />);
  }

  beforeEach(mock.module(INSIGHT_FILTER_STATE_MODEL, REACT_MODULE, OVERRIDE_REGISTRY));
  beforeEach(() => jasmine.clock().install());
  beforeEach(
    mock.inject(() => {
      spyOn(ViewStateCache, 'createCache').and.returnValue({ get: noop, put: noop, touch: noop } as any);
      State.initialize();
      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'executions', lazy: true, defaultData: [] },
        { key: 'pipelineConfigs', lazy: true, defaultData: [] },
        { key: 'runningExecutions', lazy: true, defaultData: [] },
      );
    }),
  );
  afterEach(async () => {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    component?.unmount();
    jasmine.clock().uninstall();
  });

  it('should not set loading flag to false until executions and pipeline configs have been loaded', async () => {
    initializeApplication();
    expect(component.find(Spinner).length).toBe(1);
    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    await settleInitialization();

    expect(component.find(Spinner).length).toBe(0);
  });
});
