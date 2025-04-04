import * as core from '@actions/core';
import * as git from '../git/git';
import * as util from '../util';

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

  getBranch(): string {
    // If a `branch` input is not provided, attempt to infer it from the `version`
    const inputBranch = util.getInput('branch');
    if (!inputBranch) {
      const version = util.getInput('version');
      const versionParts = version.split('.');

      if (versionParts.length == 3) {
        // Release branches are named release-<year>.<major>.x
        return `release-${versionParts[0]}.${versionParts[1]}.x`;
      } else if (versionParts.length == 2 && versionParts[0] == 'main') {
        return 'main';
      } else {
        throw new Error(
          `Cannot infer branch to determine which service versions to use: ${version} - please specify in inputs.`,
        );
      }
    } else {
      return inputBranch;
    }
  }

  getLastTag(): git.Tag | undefined {
    return git.findServiceTag(this.name, this.getBranch());
  }

  getVersion(): string {
    const globalVersionOverride = util.getInput('version-override');
    let version =
      this.inputOverrides?.version ||
      this.overrides?.version ||
      globalVersionOverride ||
      this.getLastTag()?.name;

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

  getCommit(): string {
    const commit =
      this.inputOverrides?.commit ||
      this.overrides?.commit ||
      this.getLastTag()?.sha;
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
