import { UISref, useCurrentStateAndParams, useRouter } from '@uirouter/react';
import { isEmpty, trim } from 'lodash';
import * as React from 'react';

import { BuildDetailsScrollContainerContext } from './BuildDetailsScrollContainerContext';
import { BuildInfoDetails } from './BuildInfoDetails';
import { BuildInfoSummaryCard } from './BuildInfoSummaryCard';
import { Application } from '../../application';
import { ICiBuild } from '../domain';
import { useDataSource } from '../../presentation';
import { Spinner } from '../../widgets';

import './builds.less';

interface IBuildProps {
  app: Application;
}
export function Builds({ app }: IBuildProps) {
  const dataSource = app.getDataSource('builds');
  const { data: builds, status, loaded } = useDataSource<ICiBuild[]>(dataSource);
  const {
    stateService: { go },
  } = useRouter();
  const { params, state } = useCurrentStateAndParams();
  const buildDetailsScrollContainer = React.useRef(null);

  React.useEffect(() => {
    dataSource.activate();
  }, []);

  React.useEffect(() => {
    if (!isEmpty(builds) && !state.name.includes('.build.buildTab')) {
      go('home.applications.application.builds.build.buildTab', { buildId: builds[0].id, tab: 'logs' });
    }
  }, [builds]);

  if (!loaded) {
    return (
      <div style={{ width: '100%' }} className="flex-container-h center">
        <Spinner size="medium" />
      </div>
    );
  }

  if (status === 'ERROR') {
    return (
      <div style={{ width: '100%' }}>
        <h4 className="text-center">There was an error loading builds.</h4>
      </div>
    );
  }

  const { repoType, repoProjectKey, repoSlug } = app.attributes;
  const hasAllConfig = [repoType, repoProjectKey, repoSlug].every((attr) => trim(attr));
  if (!hasAllConfig) {
    return (
      <div className="row configuration-error">
        <div className="col-md-12">
          Welcome! To get started with Builds, you'll need to add a
          <UISref to="home.applications.application.config" className="bold config">
            <a> repository to your app's configuration.</a>
          </UISref>
        </div>
      </div>
    );
  }

  if (isEmpty(builds)) {
    return (
      <div className="col-md-12">
        <span>
          Welcome! To get started with Builds, you'll need to make sure that RocketCI is configured for this app.
          <br />
          Please visit{' '}
          <a href="http://go.netflix.com/rocketci/" target="_blank">
            RocketCI
          </a>{' '}
          for more details.
        </span>
      </div>
    );
  }

  const selectedBuild = params.buildId ? builds.find((build) => build.id === params.buildId) : null;

  return (
    <>
      <div className="nav-ci">
        {builds.map((build) => (
          <BuildInfoSummaryCard
            build={build}
            isActive={params.buildId === build.id}
            key={build.id}
            onClick={() =>
              go('home.applications.application.builds.build.buildTab', { buildId: build.id, tab: 'logs' })
            }
          />
        ))}
      </div>
      <div className="build-detail" ref={buildDetailsScrollContainer}>
        {selectedBuild && (
          <BuildDetailsScrollContainerContext.Provider value={buildDetailsScrollContainer.current}>
            <BuildInfoDetails build={selectedBuild} />
          </BuildDetailsScrollContainerContext.Provider>
        )}
      </div>
    </>
  );
}
