import type { UIRouterReact } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react';
import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { ApplicationDataSource } from '../../application/service/applicationDataSource';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { REACT_MODULE } from '../../reactShims';
import { Builds } from './Builds';
import type { ICiBuild } from '../domain';

describe('Builds', () => {
  let $uiRouter: UIRouterReact;
  const build = ({
    id: 'build-1',
    number: 1,
    fullDisplayName: 'main #1',
    result: 'SUCCEEDED',
    artifacts: [],
    author: 'octopus',
    branchName: 'main',
    commitId: 'abc123',
    commitLink: 'https://example.test/commit/abc123',
    commitMessage: 'Deploy app',
    duration: 1000,
    pullRequestNumber: '',
    startTime: Date.now() - 1000,
    url: 'https://example.test/build/1',
  } as unknown) as ICiBuild;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
    }),
  );

  it('renders loaded builds inside a single page wrapper', () => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'app',
      { key: 'builds', defaultData: [] as ICiBuild[] },
      { key: 'runningBuilds', defaultData: [] as ICiBuild[], visible: false },
    );
    const buildsDataSource = app.getDataSource('builds') as ApplicationDataSource<ICiBuild[]>;
    buildsDataSource.data = [build];
    buildsDataSource.status$.next({ status: 'FETCHED', loaded: true, data: [build], lastRefresh: 0, error: null });
    app.attributes.repoType = 'github';
    app.attributes.repoProjectKey = 'spinnaker';
    app.attributes.repoSlug = 'deck';
    spyOn($uiRouter.stateService, 'go').and.returnValue(Promise.resolve(null) as any);

    const wrapper = mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <Builds app={app} />
      </UIRouterContext.Provider>,
    );

    expect(
      wrapper
        .find('.builds-page')
        .children()
        .map((child) => child.prop('className')),
    ).toEqual(['nav-ci', 'build-detail']);
  });

  it('renders configuration errors inside the page wrapper', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'builds',
      defaultData: [] as ICiBuild[],
    });
    const buildsDataSource = app.getDataSource('builds') as ApplicationDataSource<ICiBuild[]>;
    buildsDataSource.status$.next({ status: 'FETCHED', loaded: true, data: [], lastRefresh: 0, error: null });

    const wrapper = mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <Builds app={app} />
      </UIRouterContext.Provider>,
    );

    expect(wrapper.find('.builds-page').hasClass('builds-page-empty')).toBe(true);
    expect(wrapper.find('.builds-page > .configuration-error').exists()).toBe(true);
  });
});
