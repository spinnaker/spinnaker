import * as path from 'path';
import { exec } from 'child_process';

const GSUTIL_PROC_OPTS = {
  timeout: 5000,
  killSignal: 'SIGKILL',
};

export class GCSFixtureDownloader {
  public static download(bucketPathUri: string, specsRootPath: string, absFixturePath: string) {
    const relativeFixturePath = path.relative(specsRootPath, absFixturePath);
    const fullBucketDownloadUri = [bucketPathUri, relativeFixturePath].join('/');
    return new Promise((resolve, reject) => {
      const command = ['gsutil', 'cp', fullBucketDownloadUri, absFixturePath];
      const onExit = (err: Error) => {
        if (err) {
          reject(new Error(`failed to save file "${absFixturePath}" from source "${fullBucketDownloadUri}": ${err}`));
        } else {
          resolve();
        }
      };
      const gsutilProc = exec(command.join(' '), GSUTIL_PROC_OPTS, onExit);
      gsutilProc.stderr.on('data', data => {
        process.stderr.write(data);
      });
      gsutilProc.stdout.on('data', _data => {});
    });
  }
}
