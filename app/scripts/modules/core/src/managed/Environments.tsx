import React, { useState } from 'react';
import { isEqual } from 'lodash';

import { useDataSource } from '../presentation/hooks';
import { Application, ApplicationDataSource } from '../application';
import { IManagedApplicationEnvironmentSummary } from '../domain/IManagedEntity';
import { ColumnHeader } from './ColumnHeader';
import { ArtifactsList } from './ArtifactsList';
import { EnvironmentsList } from './EnvironmentsList';

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
              artifactSelected={clickedArtifact =>
                setSelectedArtifact(isEqual(clickedArtifact, selectedArtifact) ? null : clickedArtifact)
              }
            />
          </div>
          <div className={styles.environmentsColumn}>
            <ColumnHeader text="Environments" icon="search" />
            <EnvironmentsList {...environments} selectedArtifact={selectedArtifact} />
          </div>
        </div>
      </div>
    </div>
  );
}
