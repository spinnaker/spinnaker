import { sortBy } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';

import { CollapsibleSection, useApplicationContextSafe } from 'core/presentation';
import { Spinner } from 'core/widgets';

import { VersionContent } from './VersionContent';
import { VersionHeading } from './VersionHeading';
import { ManagementWarning } from '../config/ManagementWarning';
import { useFetchVersionsHistoryQuery } from '../graphql/graphql-sdk';
import { isBaking } from '../overview/artifact/utils';
import { HistoryEnvironment, PinnedVersions, VersionData } from './types';
import { spinnerProps } from '../utils/defaults';

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

const groupVersionsByShaOrBuild = (environments: HistoryEnvironment[]) => {
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

  const { data, error, loading } = useFetchVersionsHistoryQuery({
    variables: { appName: app.name, limit: 100 }, // Fetch the last 100 versions
  });

  if (loading && !data) {
    return <Spinner {...spinnerProps} message="Loading versions history..." />;
  }

  if (error) {
    console.warn(error);
    return <div style={{ width: '100%' }}>Failed to load history, please refresh and try again.</div>;
  }

  const groupedVersions = groupVersionsByShaOrBuild(data?.application?.environments || []);
  const pinnedVersions = getPinnedVersions(data?.application?.environments || []);

  return (
    <main className="VersionsHistory">
      <ManagementWarning appName={app.name} />
      {groupedVersions.map((group) => {
        return (
          <div key={group.key}>
            <CollapsibleSection
              outerDivClassName="version-item"
              toggleClassName="version-toggle"
              bodyClassName="version-body"
              expandIconType="plus"
              heading={({ chevron }) => <VersionHeading group={group} chevron={chevron} />}
            >
              <VersionContent versionData={group} pinnedVersions={pinnedVersions} />
            </CollapsibleSection>
          </div>
        );
      })}
    </main>
  );
};
