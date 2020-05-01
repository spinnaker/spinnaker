import React from 'react';
import classNames from 'classnames';

import { IManagedArtifactSummary, IManagedArtifactVersion } from '../domain/IManagedEntity';
import { ISelectedArtifactVersion } from './Environments';
import { Pill } from './Pill';

import styles from './ArtifactRow.module.css';

interface IArtifactsListProps {
  artifacts: IManagedArtifactSummary[];
  versionSelected: (version: ISelectedArtifactVersion) => void;
  selectedVersion: ISelectedArtifactVersion;
}

export function ArtifactsList({ artifacts, selectedVersion, versionSelected }: IArtifactsListProps) {
  return (
    <div>
      {artifacts.map(({ versions, name, reference }) =>
        versions.map(version => (
          <ArtifactRow
            key={`${name}-${version.version}`}
            isSelected={
              selectedVersion && selectedVersion.reference === reference && selectedVersion.version === version.version
            }
            clickHandler={versionSelected}
            version={version}
            reference={reference}
            name={name}
          />
        )),
      )}
    </div>
  );
}

interface IArtifactRowProps {
  isSelected: boolean;
  clickHandler: (artifact: ISelectedArtifactVersion) => void;
  version: IManagedArtifactVersion;
  reference: string;
  name: string;
}

export const ArtifactRow = ({
  isSelected,
  clickHandler,
  version: { version, displayName, environments, build, git },
  reference,
  name,
}: IArtifactRowProps) => (
  <div
    className={classNames(styles.ArtifactRow, { [styles.selected]: isSelected })}
    onClick={() => clickHandler({ reference, version })}
  >
    <div className={styles.content}>
      {build?.id && (
        <div className={styles.version}>
          <Pill text={`#${build.id}`} />
        </div>
      )}
      <div className={classNames(styles.text, { 'sp-margin-m-left': !build?.id })}>
        <div className={styles.sha}>{git?.commit || displayName}</div>
        <div className={styles.name}>{name}</div>
      </div>
      {/* Holding spot for status bubbles */}
    </div>
    <div className={styles.stages}>
      {environments
        .map(({ name, state }) => <span key={name} className={classNames(styles.stage, styles[state])} />)
        .reverse()}
    </div>
  </div>
);
