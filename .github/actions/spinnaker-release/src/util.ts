import * as core from '@actions/core';

export function getInput(name: string): string {
  if (!name) return '';

  // Find inputs from environment variables in both hyphenated and underscored forms
  const dehyphenated = name.replaceAll('-', '_');
  return core.getInput(name) || core.getInput(dehyphenated);
}

export function parseVersion(version: string) {
  const split = version.split('.');
  if (split.length != 3) return null;

  try {
    const major = parseInt(split[0]);
    const minor = parseInt(split[1]);
    const patch = parseInt(split[2]);

    return {
      major,
      minor,
      patch,
    };
  } catch (e) {
    core.error(e);
    return null;
  }
}
