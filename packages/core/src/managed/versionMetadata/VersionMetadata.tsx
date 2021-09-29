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
  VersionCreatedAt,
  VersionMessage,
  VersionMetadataActions,
} from './MetadataComponents';
import { formatToRelativeTimestamp, RelativeTimestamp } from '../RelativeTimestamp';
import { getLifecycleEventSummary } from '../overview/artifact/utils';
import type { QueryArtifactVersion } from '../overview/types';
import { HoverablePopover, IconTooltip } from '../../presentation';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';
import type { SingleVersionArtifactVersion } from '../versionsHistory/types';

export const getBaseMetadata = (
  version: QueryArtifactVersion | SingleVersionArtifactVersion,
): Omit<Partial<IVersionMetadataProps>, 'version'> & Pick<IVersionMetadataProps, 'version'> => {
  return {
    version: version.version,
    sha: version.gitMetadata?.commit,
    build: {
      buildNumber: version.buildNumber,
      version: version.version,
      ...getLifecycleEventSummary(version, 'BUILD'),
    },
    author: version.gitMetadata?.author,
    deployedAt: version.deployedAt,
    isDeploying: version.status === 'DEPLOYING',
    bake: getLifecycleEventSummary(version, 'BAKE'),
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
  buildsBehind,
  bake,
  isDeploying,
  pinned,
  vetoed,
  actions,
}: IVersionMetadataProps) => {
  return (
    <BaseVersionMetadata>
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
      <VersionCreatedAt createdAt={createdAt} linkProps={sha ? { sha } : { version }} />
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
      {(build?.duration || bake?.duration) && (
        <MetadataElement>
          <HoverablePopover
            delayShow={200}
            wrapperClassName="vertical"
            Component={() => (
              <>
                <LifecycleEventDetails title="Bake" {...bake} />
                <LifecycleEventDetails title="Build" {...build} />
              </>
            )}
          >
            <i className="fas fa-info-circle " />
          </HoverablePopover>
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
