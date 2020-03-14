import React from 'react';

import { IManagedArtifactSummary, IManagedArtifactVersion } from '../domain/IManagedEntity';
import { ISelectedArtifact } from './Environments';
import { Pill } from './Pill';
import { parseName } from './Frigga';

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
        versions.map((version, i) => (
          <ArtifactRow
            key={`${name}-${version.version}-${i}`} // appending index until name-version is guaranteed to be unique
            clickHandler={artifactSelected}
            version={version}
            name={name}
            stages={[4, 3, 0]}
          />
        )),
      )}
    </div>
  );
}

interface IArtifactRowProps {
  clickHandler: (artifact: ISelectedArtifact) => void;
  version: IManagedArtifactVersion;
  name: string;
  stages: any[];
}

export function ArtifactRow({ clickHandler, version, name, stages }: IArtifactRowProps) {
  const versionString = version.version;
  const { packageName, version: packageVersion, buildNumber, commit } = parseName(versionString);
  return (
    <div className={styles.ArtifactRow} onClick={() => clickHandler({ name, version: versionString })}>
      <div className={styles.content}>
        <div className={styles.version}>
          <Pill text={buildNumber ? `#${buildNumber}` : packageVersion || versionString} />
        </div>
        <div className={styles.text}>
          <div className={styles.sha}>{commit}</div>
          <div className={styles.name}>{name || packageName}</div>
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
