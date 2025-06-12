import * as Path from 'path';
import _ from 'lodash';
import { parse } from 'yaml';
import { StoredYml } from '../gcp/stored_yml';
import * as util from '../util';
import * as core from '@actions/core';
import { parseAndSortVersionsAsStr, Version } from '../versions';

export interface IllegalVersion {
  reason: string;
  version: string;
}

export interface VersionEntry {
  alias: string;
  changelog: string;
  lastUpdate: number;
  minimumHalyardVersion: string;
  version: string;
}

export function empty(): VersionsDotYml {
  return new VersionsDotYml([], '', '', []);
}

export function fromYml(yamlStr: string): VersionsDotYml {
  const parsed = parse(yamlStr);
  if (isVersionsDotYml(parsed)) {
    return new VersionsDotYml(
      parsed.illegalVersions,
      parsed.latestHalyard,
      parsed.latestSpinnaker,
      parsed.versions,
    );
  }
  throw new Error(`Invalid versions.yml - does not conform: ${yamlStr}`);
}

export async function fromCurrent(): Promise<VersionsDotYml> {
  const current = await empty().getCurrent();
  if (!current) {
    throw new Error('Unable to retrieve current versions.yml');
  }

  return fromYml(current);
}

export function isVersionsDotYml(obj: any): obj is VersionsDotYml {
  const requiredKeys = [
    'illegalVersions',
    'latestHalyard',
    'latestSpinnaker',
    'versions',
  ];
  const objKeys = new Set(Object.keys(obj));
  return requiredKeys.every((rk) => objKeys.has(rk) && obj[rk] != null);
}

export class VersionsDotYml extends StoredYml {
  illegalVersions: Array<IllegalVersion>;
  latestHalyard: string;
  latestSpinnaker: string;
  versions: Array<VersionEntry>;

  constructor(
    illegalVersions: Array<IllegalVersion>,
    latestHalyard: string,
    latestSpinnaker: string,
    versions: Array<VersionEntry>,
  ) {
    super();
    this.illegalVersions = illegalVersions;
    this.latestHalyard = latestHalyard;
    this.latestSpinnaker = latestSpinnaker;
    this.versions = versions;
  }

  isSemVer(versionStr: string): boolean {
    if (!versionStr) return false;

    const split = versionStr.split('.');
    if (split.length != 3) return false;

    if (
      isNaN(Number(split[0])) ||
      isNaN(Number(split[1])) ||
      isNaN(Number(split[2]))
    ) {
      return false;
    }

    return true;
  }

  addVersion(versionStr: string) {
    // Check to see if we already have an entry for this version - no duplicates should be allowed
    if (this.versions.map((it) => it.version).some((it) => it === versionStr)) {
      core.info(
        `Version ${versionStr} already exists in versions.yml - not updating`,
      );
      return;
    }

    // Halyard requires semver-style versions - do not allow anything else to be published
    if (!this.isSemVer(versionStr)) {
      throw new Error(
        `Version ${versionStr} is not in SemVer style - cannot publish`,
      );
    }

    // Halyard builds with the rest of everything, and is now on the same version
    // So, we can generally simplify this block and only require one version string as input
    this.versions.push({
      alias: versionStr.startsWith('main') ? versionStr : `v${versionStr}`,
      changelog: `https://spinnaker.io/changelogs/${versionStr}-changelog/`,
      lastUpdate: new Date().getTime(),
      minimumHalyardVersion: versionStr,
      version: versionStr,
    });

    // Update our latest if we need to
    this.updateLatestMetadata();
  }

  removeVersion(versionStr: string) {
    this.versions = this.versions.filter((v) => v.version !== versionStr);

    // Update our latest if we need to
    this.updateLatestMetadata();
  }

  updateLatestMetadata() {
    // Find the biggest version in the file
    const allVersionsSorted = parseAndSortVersionsAsStr(
      this.versions.map((it) => it.version),
    );

    // Update top-level metadata
    const newest = allVersionsSorted[0];
    this.latestHalyard = newest;
    this.latestSpinnaker = newest;
  }

  // Reduce the list of versions to only the most recent patch for each major/minor
  collapseVersions() {
    // Group by major/minor, then sort each group by patch, then take the first from each group
    this.versions = _(this.versions)
      .groupBy((it) => {
        const parsed = Version.parse(it.version)!;
        return `${parsed.major}.${parsed.minor}`;
      })
      .map((group) => {
        return group.sort((a, b) => {
          const parsedA = Version.parse(a.version)!;
          const parsedB = Version.parse(b.version)!;
          return parsedB.patch - parsedA.patch;
        })[0];
      })
      .value();

    // Reduce the total number of available releases to 3
    // This to retain compatibility with old buildtool-generated releases while they are supported
    // We can remove this in the future if we decide on a number different from 3
    this.versions = this.versions
      .sort((a, b) => {
        const parsedA = Version.parse(a.version)!;
        const parsedB = Version.parse(b.version)!;
        return Version.compare(parsedA, parsedB) * -1;
      })
      .slice(0, 3);

    // Update our latest if we need to
    this.updateLatestMetadata();
  }

  override getBucket(): string {
    return util.getInput('bucket');
  }

  override getBucketFilePath(): string {
    return Path.join(
      util.getInput('versions-yml-bucket-path'),
      this.getFilename(),
    );
  }

  override getFilename(): string {
    return `versions.yml`;
  }
}
