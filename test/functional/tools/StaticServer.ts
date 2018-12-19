/// <reference path="wait-on.d.ts" />

import { spawn, ChildProcess } from 'child_process';
import * as process from 'process';
import * as waitOn from 'wait-on';

const WAIT_INTERVAL_MS = 500;
const WAIT_TIMEOUT_MS = 300000;

export class StaticServer {
  private proc: ChildProcess;

  constructor(private repoRoot: string) {}

  public launch(): Promise<void | Error> {
    return new Promise((resolve, reject) => {
      this.proc = spawn('./node_modules/.bin/webpack-dev-server', ['--mode=production'], {
        cwd: this.repoRoot,
        env: {
          PATH: process.env.PATH,
          NODE_OPTIONS: '--max_old_space_size=8192',
        },
      });
      this.proc.on('error', (err: Error) => {
        reject(err);
      });
      waitOn(
        {
          interval: WAIT_INTERVAL_MS,
          timeout: WAIT_TIMEOUT_MS,
          resources: ['http-get://localhost:9000'],
        },
        (err: Error) => {
          if (err) {
            reject(new Error(`failed to launch webpack-dev-server: ${err}`));
            this.kill();
          } else {
            resolve();
          }
        },
      );
    });
  }

  public kill(): Promise<void | Error> {
    return new Promise(resolve => {
      this.proc.kill();
      resolve();
    });
  }
}
