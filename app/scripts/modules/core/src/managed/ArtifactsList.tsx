import classNames from 'classnames';
import React, { useState } from 'react';

import { ISelectedArtifactVersion } from './Environments';
import {
  IManagedArtifactSummary,
  IManagedArtifactVersion,
  IStatefulConstraint,
  StatefulConstraintStatus,
} from '../domain/IManagedEntity';
import { Pill } from './Pill';
import { IStatusBubbleStackProps, StatusBubbleStack } from './StatusBubbleStack';

import './ArtifactRow.less';

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

export const ArtifactRow = ({ isSelected, clickHandler, version: versionInfo, reference, name }: IArtifactRowProps) => {
  const { version, displayName, environments, build, git } = versionInfo;
  const [isHovered, setIsHovered] = useState(false);

  return (
    <div
      className={classNames('ArtifactRow', { selected: isSelected })}
      onClick={() => clickHandler({ reference, version })}
      onMouseOver={() => setIsHovered(true)}
      onMouseOut={() => setIsHovered(false)}
    >
      <div className="row-content">
        {build?.id && (
          <div className="version-identifier">
            <Pill bgColor={isSelected ? '#2c4b5f' : undefined} text={`#${build.id}`} />
          </div>
        )}
        <div className={classNames('version-title', { 'sp-margin-m-left': !build?.id })}>
          <div className="version-name">{git?.commit || displayName}</div>
          {name && <div className="artifact-name">{name}</div>}
        </div>
        <StatusBubbleStack
          borderColor={isSelected ? '#c7def5' : isHovered ? '#e8eaf2' : 'var(--color-alabaster)'}
          maxBubbles={3}
          statuses={getArtifactStatuses(versionInfo)}
        />
      </div>
      <div className="environment-stages">
        {environments
          .map(({ name, state }) => (
            <span
              key={name}
              className={classNames('environment-stage', state, 'text-bold flex-container-h center middle')}
              style={{ width: `${environments.length / 100}%` }}
            >
              <span className="environment-stage-name">{name}</span>
            </span>
          ))
          .reverse()}
      </div>
    </div>
  );
};

type ArtifactStatusList = IStatusBubbleStackProps['statuses'];
function getArtifactStatuses({ environments }: IManagedArtifactVersion): ArtifactStatusList {
  const statuses: ArtifactStatusList = [];
  // NOTE: The order in which entries are added to `statuses` is important. The highest priority
  // item must be inserted first.

  const isConstraintPendingManualJudgement = (constraint: IStatefulConstraint) =>
    constraint.type == 'manual-judgement' && constraint.status == StatefulConstraintStatus.PENDING;
  const requiresManualApproval = environments.some(environment =>
    environment.statefulConstraints?.some(isConstraintPendingManualJudgement),
  );
  if (requiresManualApproval) {
    statuses.push({ appearance: 'progress', iconName: 'manualJudgement' });
  }

  const isPinned = environments.some(({ pinned }) => pinned);
  if (isPinned) {
    statuses.push({ appearance: 'warning', iconName: 'pin' });
  }

  return statuses;
}
