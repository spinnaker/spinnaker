import { execWithOutput, getInput } from '../util';
import { createMergeBranchPr, setup } from './util';
import { MergeResult } from './types';

async function addRemote(remote: string) {
  return execWithOutput(
    `git remote add ${remote} https://github.com/spinnaker/${remote}.git`,
  );
}

async function fetchRemote(remote: string, ref: string) {
  return execWithOutput(`git fetch -n ${remote} ${ref}`);
}

async function mergeRemote(remote: string, ref: string) {
  const withOurs = getInput('merge-with-ours') == 'true' ? ' -X ours' : '';
  const result = await execWithOutput(
    `git merge --edit --strategy subtree -X subtree=${remote}${withOurs} ${remote}/${ref}`,
    undefined,
    {
      GIT_EDITOR: './.github/actions/update-monorepo/subtree_pull_editor.sh',
      GIT_SUBTREE: remote,
      GIT_SUBTREE_REMOTE: `${remote}/${ref}`,
    },
  );

  if (result.code != 0) {
    // If there was a problem merging a subtree, we need to abort so the next one can be attempted
    await execWithOutput('git merge --abort');
  }

  return result;
}

export async function merge(remote: string) {
  const ref = getInput('remote-ref');
  await addRemote(remote);
  await fetchRemote(remote, ref);
  return mergeRemote(remote, ref);
}

export async function mergeAll() {
  const results: MergeResult[] = [];
  await setup();

  const repos = getInput('repos').split(',');
  for (const repo of repos) {
    const result = await merge(repo);
    const isClean = result.code === 0;
    results.push({
      repo,
      isClean,
      exec: result,
    });
  }

  await createMergeBranchPr(results);

  return results;
}
