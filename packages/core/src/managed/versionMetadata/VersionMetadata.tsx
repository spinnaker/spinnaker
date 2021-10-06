import React from 'react';

import type { IVersionMetadataProps } from './MetadataComponents';
import {
  BaseVersionMetadata,
  LifecycleEventDetails,
  MetadataBadge,
  MetadataElement,
  toVetoedMetadata,
  VersionAuthor,
  VersionBuilds,
  VersionMessage,
} from './MetadataComponents';
import { formatToRelativeTimestamp, RelativeTimestamp } from '../RelativeTimestamp';
import { getLifecycleEventSummary } from '../overview/artifact/utils';
import type { QueryArtifactVersion } from '../overview/types';
import { HoverablePopover, Icon, IconTooltip } from '../../presentation';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';
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
      {deployedAt && (
        <MetadataElement>
          <IconTooltip
            tooltip="Deployed at"
            name="cloudDeployed"
            size="12px"
            wrapperClassName="metadata-icon"
            delayShow={TOOLTIP_DELAY_SHOW}
          />
          <RelativeTimestamp timestamp={deployedAt} delayShow={TOOLTIP_DELAY_SHOW} removeStyles withSuffix />
        </MetadataElement>
      )}
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
            <Icon name="bake" size="13px" />
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
