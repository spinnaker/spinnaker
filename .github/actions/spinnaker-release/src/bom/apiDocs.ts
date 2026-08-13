import * as core from '@actions/core';
import * as fs from 'fs';
import * as os from 'os';
import * as git from '../git/git';
import * as util from '../util';

const docsRepo = util.getInput('docs-repo-location');

export interface ApiDocsSource {
  service: string;
  // Path (on the local filesystem, e.g. produced by `./gradlew :gate:swagger`)
  // to an OpenAPI spec (JSON) to publish.
  specPath: string;
  // Path, relative to the root of the docs repo, that the spec should be published to.
  docsRepoTargetPath: string;
}

export function gateSource(): ApiDocsSource {
  return {
    service: 'gate',
    specPath: util.getInput('swagger-json-path'),
    docsRepoTargetPath: 'static/docs/reference/api/swagger.json',
  };
}

export class ApiDocs {
  version: string;
  sources: ApiDocsSource[];
  prUrl: string;

  constructor(version: string, sources: ApiDocsSource[]) {
    this.version = version;
    this.sources = sources;
    this.prUrl = '';
  }

  // Read + validate each spec up front so we fail loudly instead of publishing garbage
  loadSpecs(): Array<{ source: ApiDocsSource; contents: string }> {
    return this.sources.map((source) => {
      const contents = fs.readFileSync(source.specPath, 'utf-8');
      try {
        JSON.parse(contents);
      } catch (e) {
        throw new Error(
          `Spec at ${source.specPath} for ${source.service} is not valid JSON: ${e}`,
        );
      }
      return { source, contents };
    });
  }

  async publish() {
    const { v4: uuidv4 } = await import('uuid');
    const specs = this.loadSpecs();

    const [owner, repo] = docsRepo.split('/', 2);
    const folder = uuidv4();
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

    const branch = `auto-api-docs-spinnaker-${this.version}`;
    git.gitCmd(`git checkout -b ${branch}`, { cwd: docsCwd });

    for (const { source, contents } of specs) {
      const target = `${docsCwd}/${source.docsRepoTargetPath}`;
      fs.writeFileSync(target, contents);
      core.info(`Wrote ${source.service} spec to ${source.docsRepoTargetPath}`);
    }

    const commitMsg = `Automatic API docs update for Spinnaker ${this.version}`;
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
