import { execWithOutput, getInput } from '../util';
import { getOctokit } from '@actions/github';
import { MergeResult } from './types';
import * as process from 'node:process';

export const github = getOctokit(getInput('github-pat'));

// Convert a remote ref to a local ref
export function getLocalRef() {
  const remoteRef = getInput('remote-ref');
  return remoteRef == 'master' ? 'main' : remoteRef;
}

export function getMergeBranchName() {
  return `auto-merge-${getLocalRef()}`;
}

export function getRepoOwnerAndName() {
  const currentRepo = process.env.GITHUB_REPOSITORY || 'spinnaker/spinnaker';
  return currentRepo.split('/');
}

export function getGhaRunUrl() {
  const serverUrl = process.env.GITHUB_SERVER_URL;
  const repo = process.env.GITHUB_REPOSITORY;
  const runId = process.env.GITHUB_RUN_ID;
  if (!serverUrl || !repo || !runId) {
    return '';
  }
  return `${serverUrl}/${repo}/actions/runs/${runId}`;
}

// If any PR with the source branch as `auto-merge-<ref>` exists and is open, return it
export async function getMergeBranchPrIfExists() {
  const [owner, repo] = getRepoOwnerAndName();
  const pulls = await github.rest.pulls.list({
    owner,
    repo,
    state: 'open',
  });
  // The `head` filter option in the request just doesn't seem to work right, no matter what prefix I give it
  return pulls.data.filter((it) => it.head.ref == getMergeBranchName())[0];
}

export async function checkoutMergeBranch() {
  await execWithOutput(`git checkout -b ${getMergeBranchName()}`);
}

export async function pushMergeBranch() {
  // This intentionally force-pushes, as it needs to overwrite the old, stale branch
  await execWithOutput(`git push -f origin HEAD:${getMergeBranchName()}`);
}

export function generatePrBody(results: MergeResult[]) {
  let header = `### Auto-merge results for branch: ${getLocalRef()}`;
  header += `\nAction run: ${getGhaRunUrl()}`;

  let body = '';
  for (const result of results) {
    if (!result.isClean) {
      // Print instructions on how to manually resolve the conflicts
      body += `\n\nMerge failed for \`${result.repo}\` - run the following command, resolve conflicts, and push:`;

      // By default, the pull script merges `master`/`main`.  Release refs need an additional argument.
      const refArg = getLocalRef() == 'main' ? '' : `-r ${getLocalRef()} `;
      body += '\n```zsh\n';
      body += `./pull.sh ${refArg}${result.repo}`;
      // The Git editor script will format the message with individual commits imported, so it's nice to read
      // Manual resolutions should use the output of that script as the commit message
      body +=
        '\n# Resolve any conflicts, then commit with the following command:';
      body += `\ngit commit -a -F SUBTREE_MERGE_MSG`;
      body += '\n```';
      body += `\n
<details>
<summary>Output</summary>

\`\`\`zsh
${result.exec.out}
\`\`\`
</details>`;
    }
  }

  // Collect successful merges into one line
  const successes = results.filter((it) => it.isClean).map((it) => it.repo);
  body += `\n\n`;
  body += `Successfully merged: ${successes.join(', ')}`;

  return header + body;
}

export async function createMergeBranchPr(results: MergeResult[]) {
  // Generate a human-readable PR body from the results
  const body = generatePrBody(results);

  // If every merge failed, we won't have any diff - GitHub doesn't allow diff-less pulls
  const allFailed = results.every((it) => !it.isClean);
  if (allFailed) {
    console.log(body);
    throw new Error(
      'All merges failed - merge branch must be updated manually.',
    );
  }

  // Push whatever merge result we got to the auto-merge-<foo> branch at the origin
  await pushMergeBranch();

  // Create a pull from that branch we just pushed
  const [owner, repo] = getRepoOwnerAndName();
  return github.rest.pulls.create({
    owner,
    repo,
    head: getMergeBranchName(),
    base: getLocalRef(),
    title: `Auto-merge of individual repos (${getLocalRef()})`,
    body,
  });
}

export async function setup() {
  await execWithOutput('git -v');
  await execWithOutput(`git config user.email "${getInput('git-email')}"`);
  await execWithOutput(`git config user.name "${getInput('git-name')}"`);

  const existingMergeBranch = await getMergeBranchPrIfExists();
  if (existingMergeBranch) {
    throw new Error(
      `Auto-merge PR already exists - not overwriting: ${existingMergeBranch.html_url}
      Please resolve the open PR or close it to allow this script to regenerate it.`,
    );
  }
  await checkoutMergeBranch();
}
