import { setOutput } from '@actions/core';
import { mergeAll } from './git/merge';

async function main() {
  const results = await mergeAll();

  setOutput(
    'clean',
    results.filter((it) => it.isClean),
  );
  setOutput(
    'conflicts',
    results.filter((it) => !it.isClean),
  );
  setOutput('pr', 'TODO');
}

main();
