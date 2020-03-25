import React, { useState, useMemo } from 'react';
import { isEqual, keyBy } from 'lodash';

import { useDataSource } from '../presentation/hooks';
import { Application, ApplicationDataSource } from '../application';
import { IManagedApplicationEnvironmentSummary, IManagedResourceSummary } from '../domain';

import { ColumnHeader } from './ColumnHeader';
import { ArtifactsList } from './ArtifactsList';
import { EnvironmentsList } from './EnvironmentsList';
import { ArtifactDetail } from './ArtifactDetail';

import styles from './Environments.module.css';

const CONTENT_WIDTH = 1200;

export interface ISelectedArtifact {
  name: string;
  type: string;
  version: string;
}

interface IEnvironmentsProps {
  app: Application;
}

export function Environments({ app }: IEnvironmentsProps) {
  const dataSource: ApplicationDataSource<IManagedApplicationEnvironmentSummary> = app.getDataSource('environments');
  const {
    data: { environments, artifacts, resources },
  } = useDataSource(dataSource);

  const [selectedArtifact, setSelectedArtifact] = useState<ISelectedArtifact>();
  const [isFiltersOpen] = useState(false);

  const resourcesById = useMemo(() => keyBy(resources, 'id'), [resources]);
  const resourcesByEnvironment = useMemo(
    () =>
      environments.reduce((byEnvironment, { name, resources: resourceIds }) => {
        byEnvironment[name] = resourceIds.map(id => resourcesById[id]);
        return byEnvironment;
      }, {} as { [environment: string]: IManagedResourceSummary[] }),
    [environments, resourcesById],
  );

  const selectedArtifactDetails = useMemo(
    () =>
      selectedArtifact &&
      artifacts
        .find(({ name }) => name === selectedArtifact.name)
        ?.versions.find(({ version }) => version === selectedArtifact.version),
    [selectedArtifact, artifacts],
  );

  const totalContentWidth = isFiltersOpen ? CONTENT_WIDTH + 248 + 'px' : CONTENT_WIDTH + 'px';

  return (
    <div style={{ width: '100%' }}>
      <span>For there shall be no greater pursuit than that towards desired state.</span>
      <div style={{ maxWidth: totalContentWidth, display: 'flex' }}>
        {/* No filters for now but this is where they will go */}
        <div className={styles.mainContent} style={{ flex: `0 1 ${totalContentWidth}` }}>
          <div className={styles.artifactsColumn}>
            <ColumnHeader text="Artifacts" icon="artifact" />
            <ArtifactsList
              artifacts={artifacts}
              selectedArtifact={selectedArtifact}
              artifactSelected={clickedArtifact => {
                if (!isEqual(clickedArtifact, selectedArtifact)) {
                  setSelectedArtifact(clickedArtifact);
                }
              }}
            />
          </div>
          <div className={styles.environmentsColumn}>
            {/* This view switcheroo will be handled via the router soon,
                but for now let's do it in component local state.  */}
            {!selectedArtifact && (
              <>
                <ColumnHeader text="Environments" icon="environment" />
                <EnvironmentsList {...{ environments, artifacts, resourcesById }} />
              </>
            )}
            {selectedArtifact && (
              <ArtifactDetail
                name={selectedArtifact.name}
                type={selectedArtifact.type}
                version={selectedArtifactDetails}
                resourcesByEnvironment={resourcesByEnvironment}
                onRequestClose={() => setSelectedArtifact(null)}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
