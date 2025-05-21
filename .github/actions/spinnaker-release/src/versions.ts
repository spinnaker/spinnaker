import * as core from '@actions/core';

export class Version {
  major: number;
  minor: number;
  patch: number;

  constructor(major: number, minor: number, patch: number) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  static parse(version: string): Version | null {
    const split = version.split('.');
    if (split.length != 3) return null;

    try {
      const major = parseInt(split[0]);
      const minor = parseInt(split[1]);
      const patch = parseInt(split[2]);

      return new Version(major, minor, patch);
    } catch (e) {
      core.error(e);
      return null;
    }
  }

  static compare(a: Version, b: Version) {
    if (a.major > b.major) return 1;
    if (a.major < b.major) return -1;

    if (a.major === b.major) {
      if (a.minor > b.minor) return 1;
      if (a.minor < b.minor) return -1;

      if (a.minor === b.minor) {
        if (a.patch > b.patch) return 1;
        if (a.patch < b.patch) return -1;
      }
    }

    return 0;
  }

  equals(version: Version) {
    return (
      this.major === version.major &&
      this.minor === version.minor &&
      this.patch === version.patch
    );
  }

  toString() {
    return `${this.major}.${this.minor}.${this.patch}`;
  }
}

// Sorts all provided versions descending
export function sortVersions(versions: Version[]): Version[] {
  return versions.sort(Version.compare).reverse();
}

// Does the above but also parses them
export function parseAndSortVersions(versionStrs: string[]): Version[] {
  const parsed = versionStrs.map(Version.parse).filter((x) => !!x);

  // TS doesn't understand null filtering exactly apparently, so this cast is here
  return sortVersions(parsed as Version[]);
}

export function parseAndSortVersionsAsStr(versionStrs: string[]): string[] {
  return parseAndSortVersions(versionStrs).map((it) => it.toString());
}
