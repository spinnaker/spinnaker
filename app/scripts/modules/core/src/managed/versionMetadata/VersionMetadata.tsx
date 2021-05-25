import React from 'react';

import { IconTooltip } from 'core/presentation';

import {
  BaseVersionMetadata,
  IVersionMetadataProps,
  MetadataBadge,
  MetadataElement,
  VersionAuthor,
  VersionBuilds,
  VersionCreatedAt,
  VersionMessage,
  VersionMetadataActions,
} from './MetadataComponents';
import { formatToRelativeTimestamp, RelativeTimestamp } from '../RelativeTimestamp';
import { getLifecycleEventDuration, getLifecycleEventLink, getLifecycleEventSummary } from '../overview/artifact/utils';
import { QueryArtifactVersion } from '../overview/types';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';
import { SingleVersionArtifactVersion } from '../versionsHistory/types';

export const getBaseMetadata = (
  version: QueryArtifactVersion | SingleVersionArtifactVersion,
): Partial<IVersionMetadataProps> => {
  return {
    build: {
      buildNumber: version.buildNumber,
      buildLink: getLifecycleEventLink(version, 'BUILD'),
      version: version.version,
    },
    author: version.gitMetadata?.author,
    deployedAt: version.deployedAt,
    buildDuration: getLifecycleEventDuration(version, 'BUILD'),
    isDeploying: version.status === 'DEPLOYING',
    baking: getLifecycleEventSummary(version, 'BAKE'),
  };
};

export const VersionMetadata = ({
  build,
  author,
  deployedAt,
  createdAt,
  buildDuration,
  buildsBehind,
  baking,
  isDeploying,
  pinned,
  actions,
}: IVersionMetadataProps) => {
  return (
    <BaseVersionMetadata>
      {isDeploying && <MetadataBadge type="deploying" />}
      {baking?.isRunning && (
        <MetadataBadge
          type="baking"
          link={baking.link}
          tooltip={`${baking.startedAt ? formatToRelativeTimestamp(baking.startedAt, true) : ''} (Click to view task)`}
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
      <VersionCreatedAt createdAt={createdAt} />
      {buildDuration && (
        <MetadataElement>
          <IconTooltip
            tooltip="Build duration"
            name="build"
            size="12px"
            wrapperClassName="metadata-icon"
            delayShow={TOOLTIP_DELAY_SHOW}
          />
          {buildDuration}
        </MetadataElement>
      )}
      {buildsBehind ? (
        <MetadataElement>
          {buildsBehind} build{buildsBehind > 1 ? 's' : ''} behind
        </MetadataElement>
      ) : null}
      {actions && <VersionMetadataActions id={`${build?.buildNumber}-actions`} actions={actions} />}
      {pinned && <VersionMessage type="pinned" data={pinned} />}
    </BaseVersionMetadata>
  );
};
