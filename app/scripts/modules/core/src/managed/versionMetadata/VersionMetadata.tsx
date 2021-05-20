import React from 'react';

import { IconTooltip } from 'core/presentation';

import {
  BaseVersionMetadata,
  DeployingBadge,
  IVersionMetadataProps,
  MetadataElement,
  PinnedBadge,
  VersionAuthor,
  VersionBuilds,
  VersionCreatedAt,
  VersionMetadataActions,
} from './MetadataComponents';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { TOOLTIP_DELAY } from '../utils/defaults';

export const VersionMetadata = ({
  buildNumber,
  buildLink,
  author,
  deployedAt,
  createdAt,
  buildDuration,
  buildsBehind,
  isDeploying,
  isPinned,
  actions,
}: IVersionMetadataProps) => {
  return (
    <BaseVersionMetadata>
      {isDeploying && <DeployingBadge />}
      {isPinned && <PinnedBadge />}

      {buildNumber && <VersionBuilds builds={[{ buildNumber, buildLink }]} />}
      <VersionAuthor author={author} />
      {deployedAt && (
        <MetadataElement>
          <IconTooltip
            tooltip="Deployed at"
            name="cloudDeployed"
            size="12px"
            wrapperClassName="metadata-icon"
            delayShow={TOOLTIP_DELAY}
          />
          <RelativeTimestamp timestamp={deployedAt} delayShow={TOOLTIP_DELAY} removeStyles withSuffix />
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
            delayShow={TOOLTIP_DELAY}
          />
          {buildDuration}
        </MetadataElement>
      )}
      {buildsBehind ? (
        <MetadataElement>
          {buildsBehind} build{buildsBehind > 1 ? 's' : ''} behind
        </MetadataElement>
      ) : null}
      {actions && <VersionMetadataActions id={`${buildNumber}-actions`} actions={actions} />}
    </BaseVersionMetadata>
  );
};
