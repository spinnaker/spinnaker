import * as core from '@actions/core';
import dayjs from 'dayjs';
import * as git from '../git/git';
import * as fs from 'fs';
import * as os from 'os';
import * as util from '../util';
import * as uuid from 'uuid';

const monorepo = util.getInput('monorepo-location');
const docsRepo = util.getInput('docs-repo-location');

const partitions = [
  {
    title: 'Breaking Changes',
    pattern: /(.*?BREAKING CHANGE.*)/,
  },
  {
    title: 'Features',
    pattern: /((?:feat|feature)[(:].*)/,
  },
  {
    title: 'Configuration',
    pattern: /((?:config)[(:].*)/,
  },
  {
    title: 'Fixes',
    pattern: /((?:bug|fix)[(:].*)/,
  },
  {
    title: 'Other',
    pattern: /.*/,
  },
];

const conventionalCommit = /.+\((.+)\):\s*(.+)/;

export async function forVersion(
  version: string,
  previousVersion: string,
): Promise<Changelog> {
  if (!previousVersion) {
    const parsed = util.parseVersion(version);
    if (!parsed) {
      throw new Error(`Unable to parse version ${version}`);
    }

    if (parsed.patch >= 1) {
      const previousPatch = Math.max(parsed.patch - 1, 0);
      previousVersion = `${parsed.major}.${parsed.minor}.${previousPatch}`;
    } else {
      // Find the latest patch tag for the previous minor
      let previousReleaseTag = git.findTag(
        `spinnaker-release-${parsed.major}.${parsed.minor - 1}.`,
      );
      if (!previousReleaseTag) {
        // Try to find a tag for the previous major
        previousReleaseTag = git.findTag(
          `spinnaker-release-${parsed.major - 1}.`,
        );
        if (!previousReleaseTag) {
          throw new Error(
            `Could not resolve previous release tag for current version ${version}`,
          );
        }
      }
      previousVersion = previousReleaseTag.name.slice(
        'spinnaker-release-'.length,
      );
    }
  }

  return generate(version, previousVersion);
}

function filterCommits(commits: string[]) {
  return (
    commits
      // Autobump PRs
      .filter((c) => !c.includes('Autobump'))
      // Update merge commits from individual repos (other commits are still included)
      .filter((c) => !(c.includes('Merge') && c.includes(' into ')))
  );
}

async function generate(
  version: string,
  previousVersion: string,
): Promise<Changelog> {
  const parsed = util.parseVersion(version);
  if (!parsed) {
    throw new Error(`Failed to parse version ${version}`);
  }

  const tag = `spinnaker-release-${version}`;
  const prevTag = `spinnaker-release-${previousVersion}`;

  // Find commits and filter out things not relevant to the release
  const commits = filterCommits(git.changelogCommits(tag, prevTag) || []);

  // Filter certain characters to ensure Markdown compat
  commits.map((line) => {
    line = line.replace('%', '%25');
    line = line.replace('\n', '%0A');
    line = line.replace('\r', '%0D');
    return line;
  });

  // Render changelog Markdown
  let markdown = '';

  // Partition by severity of change
  let remainingLines = [...commits];
  const partitioned = partitions.map((part) => {
    const matching = remainingLines.filter((line) => part.pattern.test(line));
    remainingLines = remainingLines.filter((r) => !matching.includes(r));

    return {
      title: part.title,
      commits: matching.map((line) => {
        const [sha, ...rest] = line.split(' ');
        const shortSha = sha.substring(0, 8);
        let message = rest.join(' ');

        // Try to extract the conventional commit component
        let component = 'change';
        if (conventionalCommit.test(message)) {
          try {
            const matches = message.match(conventionalCommit);
            if (matches) {
              component = matches[1];
              message = matches[2];
            }
          } catch (e) {
            // No need to blow anything up over an error matching this
            core.warning(
              `Could not parse changelog message as conventional commit ${e.message}`,
            );
          }
        }

        return {
          sha,
          shortSha,
          component,
          message,
        };
      }),
    };
  });

  // Render partitions
  for (const partition of partitioned) {
    markdown += `\n#### ${partition.title}\n\n`;

    const lines = partition.commits
      .map((commit) => {
        const url = `https://github.com/${monorepo}/commit/${commit.sha}`;
        return `* **${commit.component}:** ${commit.message} ([${commit.shortSha}](${url}))`;
      })
      .sort();

    lines.forEach((line) => (markdown += `${line}\n`));
  }

  core.info('Found commits:');
  core.info(JSON.stringify(commits, null, 2));

  core.info('Changelog contents:');
  core.info(markdown);

  const markdownWithHeader =
    `---
title: Spinnaker Release ${version}
date: ${dayjs().format('YYYY-MM-DD HH:MM:SS +0000')}
major_minor: ${parsed.major}.${parsed.minor}
version: ${version}
---
` + markdown;

  return new Changelog(
    version,
    previousVersion,
    commits,
    markdown,
    markdownWithHeader,
  );
}

export class Changelog {
  version: string;
  previousVersion: string;
  commits: string[];
  markdown: string;
  markdownWithHeader: string;
  prUrl: string;

  constructor(
    version: string,
    previousVersion: string,
    commits: string[],
    markdown: string,
    markdownHeaderless: string,
  ) {
    this.version = version;
    this.previousVersion = previousVersion;
    this.commits = commits;
    this.markdown = markdown;
    this.markdownWithHeader = markdownHeaderless;
    this.prUrl = '';
  }

  async publish() {
    const [owner, repo] = docsRepo.split('/', 2);
    const folder = uuid.v4();
    const tmpdir = process.env.RUNNER_TEMP || os.tmpdir();

    // Clone docs repo
    git.gitCmd(`git clone https://github.com/${docsRepo} ${folder}`, {
      cwd: tmpdir,
    });

    const docsCwd = `${tmpdir}/${folder}`;
    git.gitCmd(`git config user.email "${util.getInput('git-email')}"`, {
      cwd: docsCwd,
    });
    git.gitCmd(`git config user.name "${util.getInput('git-name')}"`, {
      cwd: docsCwd,
    });

    // Commit our changelog file
    const branch = `auto-changelog-spinnaker-${this.version}`;
    git.gitCmd(`git checkout -b ${branch}`, { cwd: docsCwd });

    const docsRepoPathTarget = `${docsCwd}/content/en/changelogs/${this.version}-changelog.md`;
    fs.writeFileSync(docsRepoPathTarget, this.markdownWithHeader);

    const commitMsg = `Automatic changelog for Spinnaker ${this.version}`;
    git.gitCmd(`git add --all`, { cwd: docsCwd });
    git.gitCmd(`git commit -a -m '${commitMsg}'`, { cwd: docsCwd });

    const ghPat = util.getInput('github-pat');
    git.gitCmd(
      `git remote set-url origin https://${ghPat}@github.com/${docsRepo}.git`,
      { cwd: docsCwd },
    );
    git.gitCmd(`git push -f origin HEAD:${branch}`, { cwd: docsCwd });
    git.gitCmd(`git remote set-url origin https://github.com/${docsRepo}.git`, {
      cwd: docsCwd,
    });

    // Check if PR already exists
    const existingPrs = await git.github.rest.pulls.list({
      owner,
      repo,
      head: branch,
      base: 'master',
    });

    if (existingPrs.data.length > 0) {
      // Close it
      await git.github.rest.pulls.update({
        owner,
        repo,
        pull_number: existingPrs.data[0].number,
        state: 'closed',
      });
    }

    // Create PR
    const pull = await git.github.rest.pulls.create({
      owner,
      repo,
      head: branch,
      base: 'master',
      title: commitMsg,
    });
    this.prUrl = pull.data.html_url;
    return pull;
  }
}
