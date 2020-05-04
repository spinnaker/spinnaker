import React from 'react';
import classNames from 'classnames';

import { Pill } from './Pill';
import { StatusBubble } from './StatusBubble';

import { IManagedArtifactSummary, IManagedArtifactVersion } from '../domain/IManagedEntity';
import { ISelectedArtifactVersion } from './Environments';

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
            name={artifacts.length > 1 ? name : null}
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
  name?: string;
}

export const ArtifactRow = ({
  isSelected,
  clickHandler,
  version: { version, displayName, environments, build, git },
  reference,
  name,
}: IArtifactRowProps) => {
  const pinnedEnvironments = environments.filter(({ pinned }) => pinned).length;

  return (
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
          {name && <div className={styles.name}>{name}</div>}
        </div>
        {pinnedEnvironments > 0 && (
          <div className="sp-margin-s-right">
            <StatusBubble
              iconName="pin"
              appearance="warning"
              size="small"
              quantity={pinnedEnvironments > 1 ? pinnedEnvironments : null}
            />
          </div>
        )}
      </div>
      <div className={styles.stages}>
        {environments
          .map(({ name, state }) => <span key={name} className={classNames(styles.stage, styles[state])} />)
          .reverse()}
      </div>
    </div>
  );
};
