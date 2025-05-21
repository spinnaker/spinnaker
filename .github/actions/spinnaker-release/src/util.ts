import * as core from '@actions/core';

export function getInput(name: string): string {
  if (!name) return '';

  // Find inputs from environment variables in both hyphenated and underscored forms
  const dehyphenated = name.replaceAll('-', '_');
  return core.getInput(name) || core.getInput(dehyphenated);
}
