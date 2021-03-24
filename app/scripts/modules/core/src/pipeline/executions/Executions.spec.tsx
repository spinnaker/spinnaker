import React from 'react';
import { ReactWrapper, mount } from 'enzyme';
import { set } from 'lodash';
import { IScope, mock, noop } from 'angular';

import { Application } from 'core/application';
import { ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { INSIGHT_FILTER_STATE_MODEL } from 'core/insight/insightFilterState.model';
import { REACT_MODULE } from 'core/reactShims';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry';
import * as State from 'core/state';
import { IExecutionsProps, IExecutionsState, Executions } from './Executions';
import { Spinner } from 'core/widgets/spinners/Spinner';

describe('<Executions/>', () => {
  let component: ReactWrapper<IExecutionsProps, IExecutionsState>;
  let application: Application;
  let scope: IScope;

  function initializeApplication(data?: any) {
    set(application, 'executions.activate', noop);
    set(application, 'pipelineConfigs.activate', noop);
    if (data && data.executions) {
      application.executions.data = data.executions;
      application.executions.loaded = true;
    }
    if (data && data.pipelineConfigs) {
      application.pipelineConfigs.data = data.pipelineConfigs;
      application.pipelineConfigs.loaded = true;
    }

    component = mount(<Executions app={application} />);
  }

  beforeEach(mock.module(INSIGHT_FILTER_STATE_MODEL, REACT_MODULE, OVERRIDE_REGISTRY));
  beforeEach(
    mock.inject(($rootScope: IScope) => {
      State.initialize();
      scope = $rootScope.$new();
      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'executions', lazy: true, defaultData: [] },
        { key: 'pipelineConfigs', lazy: true, defaultData: [] },
        { key: 'runningExecutions', lazy: true, defaultData: [] },
      );
    }),
  );

  it('should not set loading flag to false until executions and pipeline configs have been loaded', (done) => {
    initializeApplication();
    expect(component.find(Spinner).length).toBe(1);
    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    scope.$digest();
    setTimeout(() => {
      component.setProps({});
      expect(component.find(Spinner).length).toBe(0);
      done();
    }, 100);
  });
});
