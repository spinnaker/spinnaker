import React from 'react';

import {
  BaseVersionMetadata,
  IVersionMetadataProps,
  MetadataBadge,
  MetadataElement,
  toVetoedMetadata,
  VersionAuthor,
  VersionBuilds,
  VersionCreatedAt,
  VersionMessage,
  VersionMetadataActions,
} from './MetadataComponents';
import { formatToRelativeTimestamp, RelativeTimestamp } from '../RelativeTimestamp';
import { getLifecycleEventDuration, getLifecycleEventLink, getLifecycleEventSummary } from '../overview/artifact/utils';
import { QueryArtifactVersion } from '../overview/types';
import { IconTooltip } from '../../presentation';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';
import { SingleVersionArtifactVersion } from '../versionsHistory/types';

export const getBaseMetadata = (
  version: QueryArtifactVersion | SingleVersionArtifactVersion,
): Omit<Partial<IVersionMetadataProps>, 'version'> & Pick<IVersionMetadataProps, 'version'> => {
  return {
    version: version.version,
    sha: version.gitMetadata?.commit,
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
    vetoed: version.veto ? toVetoedMetadata(version.veto) : undefined,
  };
};

export const VersionMetadata = ({
  version,
  sha,
  build,
  author,
  deployedAt,
  createdAt,
  buildDuration,
  buildsBehind,
  baking,
  isDeploying,
  pinned,
  vetoed,
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
      <VersionCreatedAt createdAt={createdAt} linkProps={sha ? { sha } : { version }} />
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
      {vetoed && <VersionMessage type="vetoed" data={vetoed} />}
    </BaseVersionMetadata>
  );
};
