import { RawParams, useCurrentStateAndParams, useRouter } from '@uirouter/react';
import { sortBy } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';

import { ApplicationQueryError } from '../ApplicationQueryError';
import { VersionContent } from './VersionContent';
import { VersionHeading } from './VersionHeading';
import { useFetchVersionsHistoryQuery } from '../graphql/graphql-sdk';
import { Messages } from '../messages/Messages';
import { isBaking } from '../overview/artifact/utils';
import { CollapsibleSection, useApplicationContextSafe } from '../../presentation';
import { HistoryArtifactVersion, HistoryEnvironment, PinnedVersions, VersionData } from './types';
import { spinnerProps } from '../utils/defaults';
import { Spinner } from '../../widgets';

import './VersionsHistory.less';

interface GroupedVersions {
  [key: string]: VersionData;
}

const setValueIfMissing = <Obj extends Record<string, any>, Key extends keyof Obj>(
  obj: Obj,
  key: Key,
  value?: Obj[Key],
  fn?: () => Obj[Key],
) => {
  if (!obj[key]) {
    if (fn !== undefined) {
      obj[key] = fn();
    } else if (value !== undefined) {
      obj[key] = value;
    }
  }
};

const getIsFocused = (version: HistoryArtifactVersion, params: RawParams) => {
  if (params.sha && params.sha === version.gitMetadata?.commit) {
    return true;
  }
  if (params.version?.includes(version.version)) {
    return true;
  }
  return false;
};

const groupVersionsByShaOrBuild = (environments: HistoryEnvironment[], params: RawParams) => {
  const groupedVersions: GroupedVersions = {};
  for (const env of environments) {
    for (const artifact of env.state.artifacts || []) {
      for (const version of artifact.versions || []) {
        const key = version.gitMetadata?.commit || version.buildNumber;
        if (key === undefined || key === null) continue;
        const type: VersionData['type'] = version.gitMetadata?.commit ? 'SHA' : 'BUILD_NUMBER';

        if (!groupedVersions[key]) {
          groupedVersions[key] = { key, environments: {}, type, buildNumbers: new Set(), versions: new Set() };
        }
        if (version.buildNumber) {
          groupedVersions[key].buildNumbers.add(version.buildNumber);
        }

        if (isBaking(version)) {
          groupedVersions[key].isBaking = true;
        }

        if (getIsFocused(version, params)) {
          groupedVersions[key].isFocused = true;
        }

        groupedVersions[key].versions.add(version.version);

        setValueIfMissing(groupedVersions[key], 'createdAt', undefined, () =>
          version.createdAt ? DateTime.fromISO(version.createdAt) : undefined,
        );
        setValueIfMissing(groupedVersions[key], 'gitMetadata', version.gitMetadata);

        const buildEnvironments = groupedVersions[key].environments;
        if (!buildEnvironments[env.name]) {
          buildEnvironments[env.name] = { versions: [] };
        }
        if (artifact.pinnedVersion?.version === version.version) {
          buildEnvironments[env.name].isPinned = true;
        }
        buildEnvironments[env.name].versions.push({ ...version, reference: artifact.reference, type: artifact.type });
      }
    }
  }
  return sortBy(Object.values(groupedVersions), (g) => (g.createdAt ? -1 * g.createdAt.toMillis() : -1));
};

const getPinnedVersions = (environments: HistoryEnvironment[]) => {
  const allPinnedVersions: PinnedVersions = {};
  for (const env of environments) {
    for (const { reference, pinnedVersion } of env.state.artifacts || []) {
      if (pinnedVersion) {
        setValueIfMissing(allPinnedVersions, env.name, {});
        allPinnedVersions[env.name][reference] = pinnedVersion;
      }
    }
  }
  return allPinnedVersions;
};

export const VersionsHistory = () => {
  const app = useApplicationContextSafe();
  const { params } = useCurrentStateAndParams();

  const { data, error, loading } = useFetchVersionsHistoryQuery({
    variables: { appName: app.name, limit: 100 }, // Fetch the last 100 versions
  });

  if (loading && !data) {
    return <Spinner {...spinnerProps} message="Loading versions history..." />;
  }

  if (error) {
    return <ApplicationQueryError hasApplicationData={Boolean(data?.application)} error={error} />;
  }

  const groupedVersions = groupVersionsByShaOrBuild(data?.application?.environments || [], params);
  const pinnedVersions = getPinnedVersions(data?.application?.environments || []);

  return (
    <main className="VersionsHistory">
      <Messages />
      {groupedVersions.map((group) => {
        return <SingleVersion key={group.key} versionData={group} pinnedVersions={pinnedVersions} />;
      })}
    </main>
  );
};

interface ISingleVersionProps {
  versionData: VersionData;
  pinnedVersions?: PinnedVersions;
}

const SingleVersion = ({ versionData, pinnedVersions }: ISingleVersionProps) => {
  const ref = React.useRef<HTMLDivElement>(null);
  const routerState = useRouter().stateService;

  React.useEffect(() => {
    if (versionData.isFocused && ref.current) {
      ref.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, []);

  return (
    <div ref={ref}>
      <CollapsibleSection
        outerDivClassName="version-item"
        toggleClassName="version-toggle"
        bodyClassName="version-body"
        expandIconType="arrowCross"
        defaultExpanded={versionData.isFocused}
        heading={({ chevron }) => <VersionHeading group={versionData} chevron={chevron} />}
        onToggle={(isExpanded) => {
          if (!isExpanded) return;
          routerState.go('.', { sha: versionData.key }, { location: 'replace' });
        }}
      >
        <VersionContent versionData={versionData} pinnedVersions={pinnedVersions} />
      </CollapsibleSection>
    </div>
  );
};
