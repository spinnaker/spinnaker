import { generate } from './bom/generate';
import * as core from '@actions/core';

generate().catch((reason) => {
  // Don't continue the workflow if something threw in an async function
  process.exitCode = 1;
  core.error(reason);
});
