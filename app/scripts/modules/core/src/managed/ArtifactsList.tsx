import React from 'react';

import { IManagedArtifactSummary } from '../domain/IManagedEntity';
import { ISelectedArtifact } from './Environments';
import { Pill } from './Pill';

import styles from './ArtifactRow.module.css';

interface IArtifactsListProps {
  artifacts: IManagedArtifactSummary[];
  artifactSelected: (artifact: ISelectedArtifact) => void;
  selectedArtifact: ISelectedArtifact;
}

export function ArtifactsList({ artifacts, artifactSelected }: IArtifactsListProps) {
  return (
    <div>
      {artifacts.map(({ versions, name }) =>
        versions.map(({ version }, i) => (
          <ArtifactRow
            key={`${name}-${version}-${i}`} // appending index until name-version is guaranteed to be unique
            clickHandler={artifactSelected}
            version={version}
            name={name}
            sha="abc123"
            stages={[4, 3, 0]}
          />
        )),
      )}
    </div>
  );
}

interface IArtifactRowProps {
  clickHandler: (artifact: ISelectedArtifact) => void;
  version: string;
  name: string;
  sha: string;
  stages: any[];
}

export function ArtifactRow({ clickHandler, version, name, sha, stages }: IArtifactRowProps) {
  return (
    <div className={styles.ArtifactRow} onClick={() => clickHandler({ name, version })}>
      <div className={styles.content}>
        <div className={styles.version}>
          <Pill text={version} />
        </div>
        <div className={styles.text}>
          <div className={styles.sha}>{sha}</div>
          <div className={styles.name}>{name}</div>
        </div>
        {/* Holding spot for status bubbles */}
      </div>
      <div className={styles.stages}>
        {stages.map((_stage, i) => (
          <span key={i} className={styles.stage} />
        ))}
      </div>
    </div>
  );
}
