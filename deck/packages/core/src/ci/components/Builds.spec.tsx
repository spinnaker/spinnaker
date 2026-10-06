import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { act, render, screen, within } from '@testing-library/react';
import React from 'react';

import { ApplicationDataSource } from '../../application/service/applicationDataSource';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { Builds } from './Builds';
import type { ICiBuild } from '../domain';

describe('Builds', () => {
  let router: UIRouterReact;
  const build: ICiBuild = {
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
    isRunning: false,
    projectKey: 'spinnaker',
    pullRequestNumber: '',
    pullRequestUrl: '',
    repoSlug: 'deck',
    startTime: Date.now() - 1000,
    url: 'https://example.test/build/1',
  };

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
  });

  afterEach(() => router.dispose());

  it('shows loading before rendering loaded builds inside a single page wrapper', () => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'app',
      { key: 'builds', defaultData: [] as ICiBuild[] },
      { key: 'runningBuilds', defaultData: [] as ICiBuild[], visible: false },
    );
    const buildsDataSource = app.getDataSource('builds') as ApplicationDataSource<ICiBuild[]>;
    app.attributes.repoType = 'github';
    app.attributes.repoProjectKey = 'spinnaker';
    app.attributes.repoSlug = 'deck';
    vi.spyOn(router.stateService, 'go').mockImplementation(() => Promise.resolve(null));

    const { container } = render(
      <UIRouterContext.Provider value={router}>
        <Builds app={app} />
      </UIRouterContext.Provider>,
    );

    expect(screen.getByText('Loading ...')).toBeInTheDocument();

    act(() => {
      buildsDataSource.status$.next({ status: 'FETCHED', loaded: true, data: [build], lastRefresh: 0, error: null });
    });

    expect(screen.queryByText('Loading ...')).not.toBeInTheDocument();
    const page = container.querySelector('.builds-page');
    expect(page).not.toBeNull();
    expect(Array.from(page?.children ?? []).map((child) => child.className)).toEqual(['nav-ci', 'build-detail']);
    expect(screen.getByRole('link', { name: 'abc123' })).toHaveAttribute('href', 'https://example.test/commit/abc123');
  });

  it('shows loading before rendering configuration errors inside the page wrapper', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'builds',
      defaultData: [] as ICiBuild[],
    });
    const buildsDataSource = app.getDataSource('builds') as ApplicationDataSource<ICiBuild[]>;
    const { container } = render(
      <UIRouterContext.Provider value={router}>
        <Builds app={app} />
      </UIRouterContext.Provider>,
    );

    expect(screen.getByText('Loading ...')).toBeInTheDocument();

    act(() => {
      buildsDataSource.status$.next({ status: 'FETCHED', loaded: true, data: [], lastRefresh: 0, error: null });
    });

    expect(screen.queryByText('Loading ...')).not.toBeInTheDocument();
    const page = container.querySelector('.builds-page');
    expect(page).toHaveClass('builds-page-empty');
    expect(within(page as HTMLElement).getByText(/repository to your app's configuration/i)).toBeInTheDocument();
  });
});
