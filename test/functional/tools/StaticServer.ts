import { exec, ChildProcess } from 'child_process';

export class StaticServer {
  private proc: ChildProcess;

  constructor(private repoRoot: string) {}

  public launch(): Promise<void | Error> {
    return new Promise(resolve => {
      this.proc = exec(
        './node_modules/.bin/webpack-dev-server --mode=production',
        {
          cwd: this.repoRoot,
          env: {
            PATH: process.env.PATH,
            NODE_OPTIONS: '--max_old_space_size=8192',
          },
        },
        (err, _stdout, _stderr) => {
          if (err != null) {
            reject(err);
          }
        },
      );
      this.proc.stdout.on('data', (data: Buffer) => {
        const str = String(data);
        if (str.match(/compiled/i)) {
          resolve();
        }
      });
    });
  }

  public kill(): Promise<void | Error> {
    return new Promise(resolve => {
      this.proc.kill();
      resolve();
    });
  }
}
