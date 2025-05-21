import * as core from '@actions/core';
import * as git from '../git/git';
import * as util from '../util';
import { Version } from '../versions';

// Default overrides for certain BoM entries
// Will be overridden by action input, if provided
export interface Overrides {
  commit?: string;
  version?: string;
}

export abstract class Service {
  abstract name: string;
  inputOverrides?: Overrides;
  overrides?: Overrides;

  constructor(overrides?: Overrides) {
    this.overrides = overrides;
    this.inputOverrides = this.getInputOverrides();
  }

  getLastTag(bomVersion: Version): git.Tag | undefined {
    return git.findServiceTag(this.name, bomVersion);
  }

  getVersion(bomVersion: Version): string {
    let version =
      this.inputOverrides?.version ||
      this.overrides?.version ||
      this.getLastTag(bomVersion)?.name;

    // Strip the service prefix if it's a tag
    const tagPrefix = `${this.name}-`;
    if (version?.startsWith(tagPrefix)) {
      version = version.slice(tagPrefix.length);
    }

    if (!version) {
      throw new Error(`Unable to resolve version for service ${this.name}`);
    }
    return version;
  }

  getCommit(bomVersion: Version): string {
    const commit =
      this.inputOverrides?.commit ||
      this.overrides?.commit ||
      this.getLastTag(bomVersion)?.sha;
    if (!commit) {
      throw new Error(`Unable to resolve commit for service ${this.name}`);
    }
    return commit;
  }

  // Overrides specified by service-specific inputs, which take priority over other global version overrides
  private getInputOverrides(): Overrides | undefined {
    const overridesInput: string = util.getInput('service-overrides');

    if (overridesInput) {
      for (const token in overridesInput.trim().split(',')) {
        const subtokens = token.trim().split(':');
        if (subtokens.length != 2) {
          core.warning(`Invalid BoM override from inputs: ${token}`);
        } else {
          const name = subtokens[0];
          let version = subtokens[1];
          if (name == this.name) {
            // Allow shorthand version input - clouddriver:main-1 resolves to tag clouddriver-main-1
            if (!version.startsWith(name)) {
              version = `${name}-${version}`;
            }
            const tag = git.parseTag(version);
            if (tag) {
              return {
                version: tag.name,
                commit: tag.sha,
              };
            } else {
              throw new Error(
                `Could not resolve tag specifed as service override: ${name}:${version}`,
              );
            }
          }
        }
      }
    }

    return undefined;
  }
}
