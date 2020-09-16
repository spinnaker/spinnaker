import React, { useMemo } from 'react';
import { pick, isEqual, keyBy } from 'lodash';
import { useCurrentStateAndParams, useRouter } from '@uirouter/react';
import { useTransition, animated } from 'react-spring';

import { SETTINGS } from 'core/config/settings';
import { Spinner } from 'core/widgets';
import { useDataSource } from '../presentation/hooks';
import { Application, ApplicationDataSource } from '../application';
import { IManagedApplicationEnvironmentSummary, IManagedResourceSummary } from '../domain';

import { ColumnHeader } from './ColumnHeader';
import { ArtifactsList } from './ArtifactsList';
import { EnvironmentsList } from './EnvironmentsList';
import { ArtifactDetail } from './ArtifactDetail';
import { EnvironmentsHeader } from './EnvironmentsHeader';

import './Environments.less';

const defaultGettingStartedUrl = 'https://www.spinnaker.io/guides/user/managed-delivery/getting-started/';

const paneSpring = { mass: 1, tension: 400, friction: 40 };

const overviewPaneInStyles = {
  opacity: 1,
  transform: 'translate(0, 0)',
};

const overviewPaneOutStyles = {
  opacity: 0.8,
  transform: 'translate(0, 20px)',
};

const overviewPaneTransitionConfig = {
  from: overviewPaneOutStyles,
  enter: overviewPaneInStyles,
  leave: overviewPaneOutStyles,
  config: paneSpring,
};

const detailPaneInStyles = {
  opacity: 1,
  transform: 'translate(0, 0)',
};

const detailPaneOutStyles = {
  opacity: 0,
  transform: 'translate(0, 300px)',
};

const detailPaneTransitionConfig = {
  from: detailPaneOutStyles,
  enter: detailPaneInStyles,
  leave: detailPaneOutStyles,
  config: paneSpring,
};

export interface ISelectedArtifactVersion {
  reference: string;
  version: string;
}

interface IEnvironmentsProps {
  app: Application;
}

export function Environments({ app }: IEnvironmentsProps) {
  const dataSource: ApplicationDataSource<IManagedApplicationEnvironmentSummary> = app.getDataSource('environments');
  const {
    data: { environments, artifacts, resources },
    status,
    loaded,
  } = useDataSource(dataSource);
  const loadFailure = status === 'ERROR';

  const {
    stateService: { go },
  } = useRouter();
  const { params } = useCurrentStateAndParams();

  const resourcesById = useMemo(() => keyBy(resources, 'id'), [resources]);
  const resourcesByEnvironment = useMemo(
    () =>
      environments.reduce((byEnvironment, { name, resources: resourceIds }) => {
        byEnvironment[name] = resourceIds.map(id => resourcesById[id]);
        return byEnvironment;
      }, {} as { [environment: string]: IManagedResourceSummary[] }),
    [environments, resourcesById],
  );

  const selectedVersion = useMemo<ISelectedArtifactVersion>(
    () => (params.version ? pick(params, ['reference', 'version']) : null),
    [params.reference, params.version],
  );
  const selectedArtifactDetails = useMemo(
    () => selectedVersion && artifacts.find(({ reference }) => reference === selectedVersion.reference),
    [selectedVersion?.reference, artifacts],
  );
  const selectedVersionDetails = useMemo(
    () => selectedArtifactDetails?.versions.find(({ version }) => version === selectedVersion.version),
    [selectedVersion, selectedArtifactDetails],
  );

  const overviewPaneTransition = useTransition(!selectedVersion, null, overviewPaneTransitionConfig);
  const detailPaneTransition = useTransition(
    { selectedVersion, selectedVersionDetails, selectedArtifactDetails },
    ({ selectedVersion }) => (selectedVersion ? 'show' : 'hide'),
    detailPaneTransitionConfig,
  );

  if (loadFailure) {
    return (
      <div style={{ width: '100%' }}>
        <h4 className="text-center">There was an error loading environments.</h4>
      </div>
    );
  }

  if (!loaded) {
    return (
      <div style={{ width: '100%' }}>
        <Spinner size="medium" message="Loading environments ..." />
      </div>
    );
  }

  const unmanaged = loaded && artifacts.length == 0 && resources.length == 0;
  const gettingStartedLink = SETTINGS.managedDelivery?.gettingStartedUrl || defaultGettingStartedUrl;
  if (unmanaged) {
    return (
      <div style={{ width: '100%' }}>
        Welcome! This application does not have any environments or artifacts. Check out the{' '}
        <a href={gettingStartedLink} target="_blank">
          getting started guide
        </a>{' '}
        to set some up!
      </div>
    );
  }

  return (
    <div className="Environments">
      <div className="artifacts-column">
        <ColumnHeader text="Versions" icon="artifact" />
        <ArtifactsList
          artifacts={artifacts}
          selectedVersion={selectedVersion}
          versionSelected={clickedVersion => {
            if (!isEqual(clickedVersion, selectedVersion)) {
              go(selectedVersion ? '.' : '.artifactVersion', clickedVersion);
            }
          }}
        />
      </div>
      <div className="environments-column">
        {overviewPaneTransition.map(
          ({ item: show, key, props }) =>
            show && (
              <animated.div key={key} className="environments-pane" style={props}>
                <ColumnHeader text="Environments" icon="environment" />
                <EnvironmentsHeader
                  app={app}
                  resourceInfo={{
                    managed: resources.filter(r => !r.isPaused).length,
                    total: resources.length,
                  }}
                />
                <EnvironmentsList application={app} {...{ environments, artifacts, resourcesById }} />
              </animated.div>
            ),
        )}
        {detailPaneTransition.map(
          ({ item, key, props }) =>
            item.selectedVersion && (
              <animated.div key={key} className="environments-pane" style={props}>
                <ArtifactDetail
                  application={app}
                  name={item.selectedArtifactDetails.name}
                  reference={item.selectedArtifactDetails.reference}
                  version={item.selectedVersionDetails}
                  allVersions={item.selectedArtifactDetails.versions}
                  resourcesByEnvironment={resourcesByEnvironment}
                  onRequestClose={() => go('^')}
                />
              </animated.div>
            ),
        )}
      </div>
    </div>
  );
}
