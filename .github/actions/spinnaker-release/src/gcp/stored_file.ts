import * as artifact from '@actions/artifact';
import * as core from '@actions/core';
import { storageClient } from './storage';
import * as fs from 'fs';
import * as os from 'os';
import * as Path from 'path';

export abstract class StoredFile {
  abstract getBucket(): string;
  abstract getBucketFilePath(): string;
  abstract getFilename(): string;

  getTmpdirPath(): string {
    return process.env['RUNNER_TEMP'] ?? os.tmpdir();
  }

  async getCurrent(): Promise<string | null> {
    return this.get(this.getBucket(), this.getBucketFilePath());
  }

  async get(bucket: string, bucketFilePath: string): Promise<string | null> {
    core.info(`Fetching from GCS: bucket=${bucket} file=${bucketFilePath}`);
    try {
      const response = await storageClient()
        .bucket(bucket)
        .file(bucketFilePath)
        .download();
      return response.toString();
    } catch (e) {
      core.error(e);
      return null;
    }
  }

  async publish(): Promise<void> {
    try {
      await storageClient()
        .bucket(this.getBucket())
        .file(this.getBucketFilePath())
        .save(this.toString());
      core.info(
        `Successfully published ${this.getFilename()} to GCP: ${this.getBucket()}/${this.getBucketFilePath()}`,
      );
    } catch (e) {
      core.error(
        `Failed publishing ${this.getFilename()} to GCP: ${this.getBucket()}/${this.getBucketFilePath()}`,
      );
      core.error(e);
    }
  }

  async uploadGhaArtifact(
    artifactName: string,
  ): Promise<artifact.UploadResponse | null> {
    // This needs to be written to a tmpfile, then uploaded
    try {
      const tmpfilePath = Path.join(this.getTmpdirPath(), this.getFilename());
      fs.writeFileSync(tmpfilePath, this.toString());

      const resp = await artifact
        .create()
        .uploadArtifact(artifactName, [tmpfilePath], '/', {});
      core.info(`Successfully published ${this.getFilename()} to GHA`);
      return resp;
    } catch (e) {
      core.info(`Failed publishing ${this.getFilename()} to GHA`);
      core.error(e);
      return null;
    }
  }
}
