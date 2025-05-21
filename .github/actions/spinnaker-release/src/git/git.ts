import * as core from '@actions/core';
import { execSync } from 'child_process';
import { getOctokit } from '@actions/github';
import * as util from '../util';
import { Version } from '../versions';

export interface Tag {
  name: string;
  sha: string;
}

export const github = getOctokit(util.getInput('github-pat'));

export function gitCmd(command: string, execOpts?: object): string | undefined {
  const out = gitCmdMulti(command, execOpts);
  return out ? out[0] : undefined;
}

export function gitCmdMulti(
  command: string,
  execOpts?: object,
): Array<string> | undefined {
  try {
    execOpts = execOpts || {};
    const out = execSync(command, execOpts).toString();
    return out.split(/\r?\n/).filter((line) => !!line);
  } catch (e) {
    core.error('Failed to execute Git command');
    core.error(e);
    return undefined;
  }
}

export function head(): string | undefined {
  return gitCmd('git rev-parse HEAD');
}

export function changelogCommits(
  tag: string,
  previousTag: string,
): Array<string> | undefined {
  // Default to HEAD if the current tag does not yet exist
  const tagOrHead = parseTag(tag)?.name ?? 'HEAD';
  return gitCmdMulti(
    `git log ${tagOrHead}...${previousTag} --oneline --no-abbrev-commit`,
  );
}

export function parseTag(name: string): Tag | undefined {
  const sha = gitCmd(`git rev-parse ${name}`);
  if (!sha) {
    return undefined;
  }

  return {
    name,
    sha,
  };
}

export function findServiceTag(
  service: string,
  bomVersion: Version,
): Tag | undefined {
  // Find the newest tag with the provided prefix, if exists, and parse it
  if (!service) {
    throw new Error(`Tag service must not be empty`);
  }

  return findTag(`${service}-${bomVersion.toString()}`);
}

// Tag of the form <service>-<train>-<build_number>
function isAutoIncrementTag(tag: string) {
  const split = tag.split('-');
  return split.length == 3 && !isNaN(parseInt(split.slice(-1)[0], 10));
}

// Tag of the form <service>-<release_version>
function isReleaseTag(tag: string) {
  const split = tag.split('-');
  return split.length == 2 && !isNaN(parseInt(split.slice(-1)[0], 10));
}

export function findTag(prefix: string) {
  const tags = gitCmdMulti(`git tag`)
    ?.filter((it) => it.startsWith(prefix))
    ?.filter((it) => {
      // Ensure this matches standard tag format - all other tags should be disregarded
      // A release tag looks like: <service>-<release_version> e.g. clouddriver-2025.0.2
      return isReleaseTag(it);
    })
    ?.map((it) => {
      // Parse the version portion of the release tag
      return {
        name: it,
        version: Version.parse(it.split('-')[1]),
      };
    })
    // Filter out anything that didn't parse
    ?.filter((it) => !!it.version)
    // TS doesn't understand null filtering yet, so there are !s asserting they are non-null
    // Sort all the entries by version descending
    ?.sort((a, b) => Version.compare(a.version!, b.version!) * -1);

  if (!tags || !tags.length) {
    core.warning(`No tags found for prefix ${prefix}`);
    return undefined;
  }

  return {
    name: tags[0].name,
    sha: gitCmd(`git rev-parse ${tags[0].name}`)!,
  };
}
