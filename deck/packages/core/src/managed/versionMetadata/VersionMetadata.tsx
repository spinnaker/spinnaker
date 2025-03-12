import React from 'react';

import type { IVersionMetadataProps } from './MetadataComponents';
import { METADATA_TEXT_COLOR } from './MetadataComponents';
import {
  BaseVersionMetadata,
  DeploymentStatus,
  LifecycleEventDetails,
  MetadataBadge,
  MetadataElement,
  toVetoedMetadata,
  VersionAuthor,
  VersionBuilds,
  VersionMessage,
} from './MetadataComponents';
import { formatToRelativeTimestamp } from '../RelativeTimestamp';
import { getLifecycleEventSummary, isVersionPending } from '../overview/artifact/utils';
import type { QueryArtifactVersion } from '../overview/types';
import { HoverablePopover, Icon } from '../../presentation';
import type { SingleVersionArtifactVersion } from '../versionsHistory/types';

export const getVersionCompareLinks = (version: QueryArtifactVersion | SingleVersionArtifactVersion) => {
  return {
    current: version.gitMetadata?.comparisonLinks?.toCurrentVersion,
    previous: version.gitMetadata?.comparisonLinks?.toPreviousVersion,
  };
};

export const getBaseMetadata = (
  version: QueryArtifactVersion | SingleVersionArtifactVersion,
): Partial<IVersionMetadataProps> => {
  // The return type above makes everything optional except for the version
  return {
    build: {
      buildNumber: version.buildNumber,
      version: version.version,
      ...getLifecycleEventSummary(version, 'BUILD'),
    },
    author: version.gitMetadata?.author,
    isPending: isVersionPending(version),
    deployedAt: version.deployedAt,
    isCurrent: version.isCurrent,
    isDeploying: version.status === 'DEPLOYING',
    bake: getLifecycleEventSummary(version, 'BAKE'),
    vetoed: version.veto ? toVetoedMetadata(version.veto) : undefined,
  };
};

export const VersionMetadata = ({
  build,
  author,
  deployedAt,
  isPending,
  isCurrent,
  isDeploying,
  buildsBehind,
  bake,
  pinned,
  vetoed,
}: IVersionMetadataProps) => {
  return (
    <BaseVersionMetadata>
      {isCurrent && <MetadataBadge type="deployed" />}
      {isDeploying && <MetadataBadge type="deploying" />}
      {bake?.isRunning && (
        <MetadataBadge
          type="baking"
          link={bake.link}
          tooltip={`${bake.startedAt ? formatToRelativeTimestamp(bake.startedAt, true) : ''} (Click to view task)`}
        />
      )}
      {build?.buildNumber && <VersionBuilds builds={[build]} />}
      <VersionAuthor author={author} />
      <DeploymentStatus {...{ deployedAt, isCurrent, isPending, isDeploying }} />
      {bake?.duration && (
        <MetadataElement>
          <HoverablePopover
            delayShow={200}
            wrapperClassName="vertical"
            Component={() => (
              <>
                <LifecycleEventDetails title="Bake" {...bake} />
              </>
            )}
          >
            <Icon name="bake" size="13px" color={METADATA_TEXT_COLOR} />
          </HoverablePopover>
        </MetadataElement>
      )}
      {buildsBehind ? (
        <MetadataElement>
          {buildsBehind} build{buildsBehind > 1 ? 's' : ''} behind
        </MetadataElement>
      ) : null}
      {pinned && <VersionMessage type="pinned" data={pinned} />}
      {vetoed && <VersionMessage type="vetoed" data={vetoed} />}
    </BaseVersionMetadata>
  );
};
