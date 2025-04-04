import { Storage } from '@google-cloud/storage';
import { CredentialBody } from 'google-auth-library';
import * as core from '@actions/core';
import * as util from '../util';
import * as fs from 'fs';

let _storageClient: Storage = null!;

function getCredentials() {
  // Can be content (in an action) or a path (elsewhere)
  const credentialsJsonOrPath = util.getInput('credentials-json');
  if (!credentialsJsonOrPath) {
    throw new Error(
      'No GCP credentials provided - please set option `credentials-json`',
    );
  }

  const isJson = credentialsJsonOrPath.indexOf('{') !== -1;
  const credentialsContent = isJson
    ? credentialsJsonOrPath
    : fs.readFileSync(credentialsJsonOrPath).toString();

  try {
    return JSON.parse(credentialsContent) as CredentialBody;
  } catch (e) {
    core.error('Failed to parse GCP credentials');
    throw e;
  }
}

export function storageClient(): Storage {
  if (!_storageClient) {
    var credentials = getCredentials();
    _storageClient = new Storage({
      credentials,
    });
  }

  return _storageClient;
}
