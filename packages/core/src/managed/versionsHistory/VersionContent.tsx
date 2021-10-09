import { flatten, omit, uniq } from 'lodash';
import React from 'react';

import { ArtifactActions } from '../artifactActions/ArtifactActions';
import { BaseEnvironment } from '../environmentBaseElements/BaseEnvironment';
import { EnvironmentItem } from '../environmentBaseElements/EnvironmentItem';
import { EnvironmentsRender, useOrderedEnvironment } from '../environmentBaseElements/EnvironmentsRender';
import type { FetchVersionQuery } from '../graphql/graphql-sdk';
import { useFetchVersionQuery } from '../graphql/graphql-sdk';
import type { ITaskArtifactVersionProps } from '../overview/artifact/ArtifactVersionTasks';
import { ArtifactVersionTasks } from '../overview/artifact/ArtifactVersionTasks';
import { Constraints } from '../overview/artifact/Constraints';
import { useCreateVersionRollbackActions } from '../overview/artifact/useCreateRollbackActions.hook';
import { extractVersionRollbackDetails, isVersionVetoed } from '../overview/artifact/utils';
import { useApplicationContextSafe } from '../../presentation';
import { LoadingAnimation } from '../../presentation/LoadingAnimation';
import type {
  HistoryArtifactVersionExtended,
  PinnedVersions,
  SingleVersionArtifactVersion,
  VersionData,
} from './types';
import type { VersionMessageData } from '../versionMetadata/MetadataComponents';
import { toPinnedMetadata } from '../versionMetadata/MetadataComponents';
import { getBaseMetadata, getVersionCompareLinks, VersionMetadata } from '../versionMetadata/VersionMetadata';

import './VersionsHistory.less';

interface IVersionInEnvironmentProps {
  environment: string;
  isPreview?: boolean;
  version: HistoryArtifactVersionExtended;
  envPinnedVersions?: PinnedVersions[keyof PinnedVersions];
  loading: boolean;
  detailedVersionData: SingleVersionArtifactVersion | undefined;
}

const VersionInEnvironment = ({
  environment,
  version,
  isPreview,
  envPinnedVersions,
  detailedVersionData,
  loading,
}: IVersionInEnvironmentProps) => {
  let pinnedData: VersionMessageData | undefined;
  const currentPinnedVersion = envPinnedVersions?.[version.reference];
  if (currentPinnedVersion && currentPinnedVersion.buildNumber === version.buildNumber) {
    pinnedData = toPinnedMetadata(currentPinnedVersion);
  }

  const actions = useCreateVersionRollbackActions({
    environment,
    reference: version.reference,
    version: version.version,
    isVetoed: isVersionVetoed(version),
    isPinned: Boolean(pinnedData),
    isCurrent: version.isCurrent,
    selectedVersion: extractVersionRollbackDetails(version),
  });

  const versionProps: ITaskArtifactVersionProps = {
    environment,
    reference: version.reference,
    version: version.version,
    isCurrent: version.isCurrent,
  };

  return (
    <EnvironmentItem
      title={version.reference}
      iconName="artifact"
      iconTooltip={`Artifact - ${version.type}`}
      size="small"
    >
      <VersionMetadata
        key={version.id}
        {...(detailedVersionData ? omit(getBaseMetadata(detailedVersionData), 'author', 'buildDuration') : undefined)}
        pinned={pinnedData}
      />
      {!isPreview && (
        <ArtifactActions
          buildNumber={version.buildNumber}
          version={version.version}
          actions={actions}
          compareLinks={detailedVersionData ? getVersionCompareLinks(detailedVersionData) : undefined}
          className="sp-margin-s-yaxis"
        />
      )}
      {loading && <LoadingAnimation />}
      <Constraints
        constraints={detailedVersionData?.constraints}
        versionProps={{ environment, reference: version.reference, version: version.version }}
        expandedByDefault={false}
      />
      <ArtifactVersionTasks type="Verification" artifact={versionProps} tasks={detailedVersionData?.verifications} />
      <ArtifactVersionTasks type="Post deploy" artifact={versionProps} tasks={detailedVersionData?.postDeploy} />
    </EnvironmentItem>
  );
};

const getDetailedVersionData = ({
  environment,
  version,
  versionsDetails,
}: {
  environment: string;
  version: HistoryArtifactVersionExtended;
  versionsDetails: FetchVersionQuery | undefined;
}) => {
  const environmentData = versionsDetails?.application?.environments?.find((env) => env.name === environment);
  const artifactData = environmentData?.state.artifacts?.find((artifact) => artifact.reference === version.reference);
  return artifactData?.versions?.find((v) => v.version === version.version);
};

interface IVersionContentProps {
  versionData: VersionData;
  pinnedVersions?: PinnedVersions;
}

/**
 * VersionContent includes all of the environments and builds that are associated with a single commit sha (unless if the sha is missing)
 */
export const VersionContent = ({ versionData, pinnedVersions }: IVersionContentProps) => {
  const ref = React.useRef<HTMLDivElement | null>(null);
  const { environments, ...renderProps } = useOrderedEnvironment(ref, Object.entries(versionData.environments));
  const app = useApplicationContextSafe();

  // Create a list of all the versions of the current commit. Usually, there will be only one.
  const versions = uniq(flatten(environments.map(([_, env]) => env.versions.map((v) => v.version))));
  const { data: versionsDetails, loading } = useFetchVersionQuery({
    variables: { appName: app.name, versions },
  });

  return (
    <EnvironmentsRender {...renderProps} ref={ref}>
      {environments.map(([env, { versions, isPreview, basedOn, gitMetadata }]) => {
        return (
          <BaseEnvironment
            key={env}
            name={env}
            basedOn={basedOn}
            gitMetadata={gitMetadata}
            isPreview={isPreview}
            size="small"
          >
            {/* Usually there will be only one version per artifact */}
            {versions.map((version) => (
              <VersionInEnvironment
                environment={env}
                key={version.id}
                version={version}
                isPreview={isPreview}
                envPinnedVersions={pinnedVersions?.[env]}
                loading={loading}
                detailedVersionData={getDetailedVersionData({ environment: env, version, versionsDetails })}
              />
            ))}
          </BaseEnvironment>
        );
      })}
    </EnvironmentsRender>
  );
};
