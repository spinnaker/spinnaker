import React from 'react';
import classNames from 'classnames';

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

export function ArtifactsList({ artifacts, selectedArtifact, artifactSelected }: IArtifactsListProps) {
  return (
    <div>
      {artifacts.map(({ versions, name, type }) =>
        versions.map((version, i) => (
          <ArtifactRow
            key={`${name}-${version.version}-${i}`} // appending index until name-version is guaranteed to be unique
            isSelected={
              selectedArtifact &&
              selectedArtifact.name === name &&
              selectedArtifact.type === type &&
              selectedArtifact.version === version.version
            }
            clickHandler={artifactSelected}
            version={version}
            name={name}
            type={type}
          />
        )),
      )}
    </div>
  );
}

interface IArtifactRowProps {
  isSelected: boolean;
  clickHandler: (artifact: ISelectedArtifact) => void;
  version: IManagedArtifactVersion;
  name: string;
  type: string;
}

export function ArtifactRow({ isSelected, clickHandler, version, name, type }: IArtifactRowProps) {
  const versionString = version.version;
  const { packageName, version: packageVersion, buildNumber, commit } = parseName(versionString);
  return (
    <div
      className={classNames(styles.ArtifactRow, { [styles.selected]: isSelected })}
      onClick={() => clickHandler({ name, type, version: versionString })}
    >
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
        {version.environments.map(({ name, state }) => (
          <span key={name} className={classNames(styles.stage, styles[state])} />
        ))}
      </div>
    </div>
  );
}
