import * as Path from 'path';
import { parse } from 'yaml';
import { StoredYml } from '../gcp/stored_yml';
import * as util from '../util';
import * as core from '@actions/core';

export interface IllegalVersion {
  reason: string;
  version: string;
}

export interface Version {
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
  versions: Array<Version>;

  constructor(
    illegalVersions: Array<IllegalVersion>,
    latestHalyard: string,
    latestSpinnaker: string,
    versions: Array<Version>,
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
  }

  removeVersion(versionStr: string) {
    this.versions = this.versions.filter((v) => v.version !== versionStr);
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
