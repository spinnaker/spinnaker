import * as express from 'express';

export class StaticServer {
  private app: any;
  private server: any;

  constructor(private directory: string, private port: number) {}

  public launch(): Promise<void | Error> {
    this.app = express();
    this.app.use(express.static(this.directory));
    return new Promise((resolve, reject) => {
      this.app.on('error', (err: Error) => {
        reject(err);
      });
      this.server = this.app.listen(this.port, () => {
        resolve();
      });
    });
  }

  public kill(): Promise<void | Error> {
    return new Promise((resolve, reject) => {
      this.server.close((err: Error) => {
        if (err) {
          reject(err);
        } else {
          resolve();
        }
      });
    });
  }
}
