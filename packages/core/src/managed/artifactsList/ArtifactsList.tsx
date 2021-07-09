import classNames from 'classnames';
import { DateTime } from 'luxon';
import React, { useEffect, useMemo, useState } from 'react';

import { Icon, IconNames } from '@spinnaker/presentation';

import { ISelectedArtifactVersion } from '../Environments';
import { Pill } from '../Pill';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { IStatusBubbleStackProps, StatusBubbleStack } from './StatusBubbleStack';
import { constraintsManager } from '../constraints/registry';
import { IConstraint, IManagedArtifactSummary, IManagedArtifactVersion } from '../../domain/IManagedEntity';

import './ArtifactRow.less';

interface IArtifactsListProps {
  artifacts: IManagedArtifactSummary[];
  versionSelected: (version: ISelectedArtifactVersion) => void;
  selectedVersion?: ISelectedArtifactVersion;
}

export function ArtifactsList({ artifacts, selectedVersion, versionSelected }: IArtifactsListProps) {
  return (
    <>
      {artifacts.map(({ versions, name, reference }) =>
        versions.map((version) => (
          <ArtifactRow
            key={`${name}-${version.version}`}
            isSelected={Boolean(
              selectedVersion && selectedVersion.reference === reference && selectedVersion.version === version.version,
            )}
            clickHandler={versionSelected}
            version={version}
            reference={reference}
            name={artifacts.length > 1 ? reference : undefined}
          />
        )),
      )}
    </>
  );
}

const getVersionIcon = ({ git }: IManagedArtifactVersion) => {
  if (git?.pullRequest?.number) {
    return 'spCIPullRequest';
  } else if (git?.commitInfo) {
    return 'spCIBranch';
  } else {
    return null;
  }
};

const getVersionSecondarySummary = ({ git }: IManagedArtifactVersion) => {
  if (git?.pullRequest?.number) {
    return `PR #${git.pullRequest.number} — ${git?.author}`;
  } else if (git?.branch) {
    return `${git.branch} — ${git?.author}`;
  } else {
    return null;
  }
};

interface IArtifactRowProps {
  isSelected: boolean;
  clickHandler: (artifact: ISelectedArtifactVersion) => void;
  version: IManagedArtifactVersion;
  reference: string;
  name?: string;
}

export const ArtifactRow = ({ isSelected, clickHandler, version: versionInfo, reference, name }: IArtifactRowProps) => {
  const { version, displayName, createdAt, environments, build, git } = versionInfo;
  const [isHovered, setIsHovered] = useState(false);
  const rowRef = React.useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    // Why does the call to scrollIntoView() have to be deferred for 100ms? A couple reasons:
    //
    // 1. When quickly moving between versions, giving a very short window to cancel the scroll
    //    makes the experience less janky. Nobody needs their sidebars to dance and shuffle.
    // 2. For some reason trying to call scrollIntoView in the same event loop just does not
    //    work at all. The same is true for a setTimeout with no delay. DOM read/write ops are weird
    //    and there is not a great practical justification for solving that mystery here and now.
    const timeout = setTimeout(() => {
      if (isSelected && rowRef.current && rowRef.current.parentElement) {
        // *** VERY BRITTLE ASSUMPTION WARNING ***
        // This code assumes that the direct parent element of a row is the scrollable container.
        // If a wrapper element of any kind is added, this code will need to be modified
        // to search upward in the tree and find the scrollable container.
        const { top, bottom } = rowRef.current.getBoundingClientRect();
        const { top: parentTop, bottom: parentBottom } = rowRef.current.parentElement.getBoundingClientRect();
        const isInView = top < parentBottom && bottom > parentTop;

        !isInView && rowRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 100);

    return () => clearTimeout(timeout);
  }, [isSelected, rowRef.current]);

  const versionIcon = getVersionIcon(versionInfo);
  const secondarySummary = getVersionSecondarySummary(versionInfo);
  const timestamp = useMemo(() => createdAt && DateTime.fromISO(createdAt), [createdAt]);

  return (
    <div
      ref={rowRef}
      className={classNames('ArtifactRow', { selected: isSelected })}
      onClick={() => clickHandler({ reference, version })}
      onMouseOver={() => setIsHovered(true)}
      onMouseOut={() => setIsHovered(false)}
    >
      <div className="row-content flex-container-v left sp-padding-m-top sp-padding-l-bottom sp-padding-m-xaxis">
        {(build?.number || build?.id) && (
          <div className="row-middle-section flex-container-h space-between middle sp-margin-s-bottom">
            <Pill bgColor={isSelected ? '#2e4b5f' : undefined} text={`#${build.number || build.id} ${name || ''}`} />
            {timestamp && <RelativeTimestamp timestamp={timestamp} />}
          </div>
        )}
        <div className="row-middle-section flex-container-h space-between">
          <div className="version-title">
            <div className="flex-container-h middle text-semibold">
              {versionIcon && (
                <span className="flex-container-h middle center sp-margin-xs-right">
                  <Icon name={versionIcon} size="extraSmall" />
                </span>
              )}{' '}
              <span className="version-name">{git?.commitInfo?.message || displayName}</span>
            </div>
            {secondarySummary && <div className="version-secondary-summary">{secondarySummary}</div>}
          </div>
          <div className="sp-margin-s-left">
            <StatusBubbleStack
              borderColor={isSelected ? '#dbe5eb' : isHovered ? '#e8eaf2' : 'var(--color-alabaster)'}
              maxBubbles={3}
              statuses={getArtifactStatuses(versionInfo)}
            />
          </div>
        </div>
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

const ignoredConstraintTypes: Array<IConstraint['type']> = ['depends-on'];

type ArtifactStatusList = IStatusBubbleStackProps['statuses'];
function getArtifactStatuses({ environments, lifecycleSteps }: IManagedArtifactVersion): ArtifactStatusList {
  const statuses: ArtifactStatusList = [];

  // NOTE: The order in which entries are added to `statuses` is important. The highest priority
  // item must be inserted first.

  const preDeploymentSteps = lifecycleSteps?.filter(
    ({ scope, type, status }) =>
      scope === 'PRE_DEPLOYMENT' && ['BUILD', 'BAKE'].includes(type) && ['RUNNING', 'FAILED'].includes(status),
  );

  if (preDeploymentSteps && preDeploymentSteps.length > 0) {
    // These steps come in with chronological ordering, but we need reverse-chronological orddering for display
    preDeploymentSteps.reverse().forEach(({ type, status }) => {
      statuses.push({
        iconName: type === 'BUILD' ? 'build' : 'bake',
        appearance: status === 'RUNNING' ? 'progress' : 'error',
      });
    });
  }

  const pendingConstraintIcons = new Set<IconNames>();
  const failedConstraintIcons = new Set<IconNames>();

  environments.forEach((environment) => {
    if (environment.state === 'skipped') {
      return;
    }

    environment.constraints?.forEach((constraint: IConstraint) => {
      const icon = constraintsManager.getIcon(constraint);
      if (constraint.status === 'PENDING' && !ignoredConstraintTypes.includes(constraint.type)) {
        pendingConstraintIcons.add(icon);
      } else if (constraint.status === 'FAIL' || constraint.status === 'OVERRIDE_FAIL') {
        failedConstraintIcons.add(icon);
      }
    });
  });

  pendingConstraintIcons.forEach((iconName) => {
    statuses.push({ appearance: 'progress', iconName });
  });
  failedConstraintIcons.forEach((iconName) => {
    statuses.push({ appearance: 'error', iconName });
  });

  const isPinned = environments.some(({ pinned }) => pinned);
  if (isPinned) {
    statuses.push({ appearance: 'warning', iconName: 'pin' });
  }

  return statuses;
}
