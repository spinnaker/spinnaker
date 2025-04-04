import * as core from '@actions/core';
import * as exec from '@actions/exec';
import { ExecResult } from './types';

export function getInput(name: string): string {
  if (!name) return '';

  // Find inputs from environment variables in both hyphenated and underscored forms
  const dehyphenated = name.replaceAll('-', '_');
  return core.getInput(name) || core.getInput(dehyphenated);
}

export async function execWithOutput(
  cmd: string,
  args?: string[],
  env?: { [key: string]: string },
): Promise<ExecResult> {
  let out = '';
  let err = '';

  const options: exec.ExecOptions = {
    ignoreReturnCode: true,
  };
  options.listeners = {
    stdout: (data: Buffer) => {
      out += data.toString();
    },
    stderr: (data: Buffer) => {
      err += data.toString();
    },
  };

  options.env = env || {};

  const code = await exec.exec(cmd, args, options);
  return {
    cmd,
    out,
    err,
    code,
  };
}
