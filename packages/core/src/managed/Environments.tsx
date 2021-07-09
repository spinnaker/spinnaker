import { useCurrentStateAndParams, useRouter } from '@uirouter/react';
import { isEqual, keyBy, pick } from 'lodash';
import React, { useMemo } from 'react';
import { animated, useTransition } from 'react-spring';

import { ColumnHeader } from './ColumnHeader';
import { Environments2, getIsNewUI, UISwitcher } from './Environments2';
import { EnvironmentsHeader } from './EnvironmentsHeader';
import { EnvironmentsList } from './EnvironmentsList';
import { UnmanagedMessage } from './UnmanagedMessage';
import { Application, ApplicationDataSource } from '../application';
import { ArtifactDetail } from './artifactDetail/ArtifactDetail';
import { ArtifactsList } from './artifactsList/ArtifactsList';
import { IManagedApplicationEnvironmentSummary, IManagedResourceSummary } from '../domain';
import { useDataSource } from '../presentation/hooks';
import { Spinner } from '../widgets';

import './Environments.less';

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

export const Environments: React.FC<IEnvironmentsProps> = (props) => {
  const isNewUI = getIsNewUI();
  return (
    <>
      {isNewUI ? <Environments2 /> : <EnvironmentsOld {...props} />}
      <UISwitcher />
    </>
  );
};

export const EnvironmentsOld: React.FC<IEnvironmentsProps> = ({ app }) => {
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
        byEnvironment[name] = resourceIds.map((id) => resourcesById[id]);
        return byEnvironment;
      }, {} as { [environment: string]: IManagedResourceSummary[] }),
    [environments, resourcesById],
  );

  const selectedVersion = useMemo<ISelectedArtifactVersion | undefined>(
    () => (params.version ? (pick(params, ['reference', 'version']) as ISelectedArtifactVersion) : undefined),
    [params.reference, params.version],
  );
  const selectedArtifactDetails = useMemo(
    () => selectedVersion && artifacts.find(({ reference }) => reference === selectedVersion.reference),
    [selectedVersion?.reference, artifacts],
  );
  const selectedVersionDetails = useMemo(
    () => selectedArtifactDetails?.versions.find(({ version }) => version === selectedVersion?.version),
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
  if (unmanaged) {
    return <UnmanagedMessage />;
  }

  return (
    <div className="Environments">
      <div className="artifacts-column flex-container-v">
        <ColumnHeader text="Versions" icon="artifact" />
        <div className="artifacts-column-scroll-container">
          <ArtifactsList
            artifacts={artifacts}
            selectedVersion={selectedVersion}
            versionSelected={(clickedVersion) => {
              if (!isEqual(clickedVersion, selectedVersion)) {
                go(selectedVersion ? '.' : '.artifactVersion', clickedVersion);
              }
            }}
          />
        </div>
      </div>
      <div className="environments-column">
        {overviewPaneTransition.map(
          ({ item: show, key, props }) =>
            show && (
              <animated.div key={key} className="environments-pane flex-container-v" style={props}>
                <div className="flex-container-v" style={{ overflow: 'hidden' }}>
                  <div className="sp-margin-m-right">
                    <ColumnHeader text="Environments" icon="environment" />
                  </div>
                  <div className="environments-column-scroll-container">
                    <div className="sp-margin-m-yaxis sp-margin-m-right">
                      <EnvironmentsHeader
                        app={app}
                        resourceInfo={{
                          managed: resources.filter((r) => !r.isPaused).length,
                          total: resources.length,
                        }}
                      />
                      <EnvironmentsList application={app} {...{ environments, artifacts, resourcesById }} />
                    </div>
                  </div>
                </div>
              </animated.div>
            ),
        )}
        {detailPaneTransition.map(
          ({ item, key, props }) =>
            item.selectedVersion &&
            item.selectedArtifactDetails &&
            item.selectedVersionDetails && (
              <animated.div key={key} className="environments-pane flex-container-v" style={props}>
                <ArtifactDetail
                  application={app}
                  name={item.selectedArtifactDetails.name}
                  reference={item.selectedArtifactDetails.reference}
                  version={item.selectedVersionDetails}
                  allVersions={item.selectedArtifactDetails.versions}
                  allEnvironments={environments}
                  showReferenceNames={artifacts.length > 1}
                  resourcesByEnvironment={resourcesByEnvironment}
                  onRequestClose={() => go('^')}
                />
              </animated.div>
            ),
        )}
      </div>
    </div>
  );
};
