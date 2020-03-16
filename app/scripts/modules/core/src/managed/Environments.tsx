import React, { useState } from 'react';
import { isEqual } from 'lodash';

import { useDataSource } from '../presentation/hooks';
import { Application, ApplicationDataSource } from '../application';
import { IManagedApplicationEnvironmentSummary } from '../domain/IManagedEntity';
import { ColumnHeader } from './ColumnHeader';
import { ArtifactsList } from './ArtifactsList';
import { EnvironmentsList } from './EnvironmentsList';
import { ArtifactDetail } from './ArtifactDetail';

import styles from './Environments.module.css';

const CONTENT_WIDTH = 1200;

export interface ISelectedArtifact {
  name: string;
  version: string;
}

interface IEnvironmentsProps {
  app: Application;
}

export function Environments(props: IEnvironmentsProps) {
  const { app } = props;
  const dataSource: ApplicationDataSource<IManagedApplicationEnvironmentSummary> = app.getDataSource('environments');
  const { data: environments } = useDataSource(dataSource);
  const [selectedArtifact, setSelectedArtifact] = useState<ISelectedArtifact>();
  const [isFiltersOpen] = useState(false);

  const totalContentWidth = isFiltersOpen ? CONTENT_WIDTH + 248 + 'px' : CONTENT_WIDTH + 'px';

  const selectedArtifactDetails =
    selectedArtifact &&
    environments.artifacts
      .find(({ name }) => name === selectedArtifact.name)
      ?.versions.find(({ version }) => version === selectedArtifact.version);

  return (
    <div style={{ width: '100%' }}>
      <span>For there shall be no greater pursuit than that towards desired state.</span>
      <div style={{ maxWidth: totalContentWidth, display: 'flex' }}>
        {/* No filters for now but this is where they will go */}
        <div className={styles.mainContent} style={{ flex: `0 1 ${totalContentWidth}` }}>
          <div className={styles.artifactsColumn}>
            <ColumnHeader text="Artifacts" icon="search" />
            <ArtifactsList
              {...environments}
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
                <ColumnHeader text="Environments" icon="search" />
                <EnvironmentsList {...environments} />
              </>
            )}
            {selectedArtifact && (
              <ArtifactDetail
                name={selectedArtifact.name}
                version={selectedArtifactDetails}
                onRequestClose={() => setSelectedArtifact(null)}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
