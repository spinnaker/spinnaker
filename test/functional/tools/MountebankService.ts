import * as fs from 'fs';
import { spawn, ChildProcess } from 'child_process';
import * as request from 'request-promise-native';

const STARTUP_TIMEOUT_MS = 5000;

export class MountebankService {
  private process: ChildProcess;

  public static builder(): MountebankServiceBuilder {
    return new MountebankServiceBuilder();
  }

  constructor(private options: MountebankServiceOptions) {}

  public launchServer(): Promise<any | Error> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        reject('mountebank server took too long to start');
      }, STARTUP_TIMEOUT_MS);
      if (this.process == null) {
        this.process = spawn(this.options.mountebankPath, ['--port', String(this.options.mountebankPort)]);
        this.process.stdout.on('data', data => {
          const str = String(data);
          if (str.includes('now taking orders')) {
            resolve();
          }
          this.options.onStdOut(str);
        });
        this.process.stderr.on('data', data => {
          const str = String(data);
          reject(str);
          this.options.onStdErr(str);
        });
        this.process.on('close', code => {
          this.options.onClose(code);
        });
      }
    });
  }

  public kill() {
    if (this.process) {
      this.process.kill();
      this.process = null;
    }
  }

  public createImposterFromFixtureFile(filepath: string, authpath: string): request.RequestPromise<any> | Promise<any> {
    console.log(`Creating imposter\n- Fixture File: ${filepath}\n- Auth File: ${authpath}`);
    try {
      const rawFixture = fs.readFileSync(filepath, { encoding: 'utf8' });
      const fixture = JSON.parse(rawFixture);
      const rawAuth = fs.readFileSync(authpath, { encoding: 'utf8' });
      const auth = JSON.parse(rawAuth);
      if (!fixture) {
        throw new Error(`no fixture found: ${filepath}`);
      }
      if (!fixture.stubs) {
        throw new Error(`found fixture does not have any response stubs: ${filepath}`);
      }
      if (!auth) {
        throw new Error(`no auth fixture found: ${authpath}`);
      }
      fixture.stubs.push(auth);
      return request({
        method: 'post',
        json: true,
        uri: `http://localhost:${this.options.mountebankPort}/imposters`,
        body: fixture,
      });
    } catch (e) {
      // Clean up on failure
      return this.removeImposters().then(() => {
        throw e;
      });
    }
  }

  public removeImposters(): request.RequestPromise<any> {
    return request({
      method: 'delete',
      json: true,
      uri: `http://localhost:${this.options.mountebankPort}/imposters`,
      body: {
        port: this.options.gatePort,
        protocol: 'http',
        stubs: [
          {
            responses: [
              {
                proxy: {
                  to: `http://localhost:${this.options.imposterPort}`,
                  predicateGenerators: [
                    {
                      matches: { method: true, path: true, query: true },
                    },
                  ],
                },
              },
            ],
          },
        ],
      },
    });
  }

  public beginRecording(): request.RequestPromise<any> {
    return request.post({
      method: 'post',
      json: true,
      uri: `http://localhost:${this.options.mountebankPort}/imposters`,
      body: {
        port: this.options.imposterPort,
        protocol: 'http',
        stubs: [
          {
            responses: [
              {
                proxy: {
                  to: `http://localhost:${this.options.gatePort}`,
                  predicateGenerators: [
                    {
                      matches: { method: true, path: true, query: true },
                      caseSensitive: true,
                    },
                  ],
                },
              },
            ],
          },
        ],
      },
    });
  }

  public saveRecording(filepath: string): Promise<any> {
    const { mountebankPort, imposterPort } = this.options;
    return request
      .get(`http://localhost:${mountebankPort}/imposters/${imposterPort}?replayable=true&removeProxies=true`)
      .then((res: any) => {
        fs.writeFileSync(filepath, res);
      });
  }
}

export class MountebankServiceOptions {
  public mountebankPath: string = 'node_modules/.bin/mb';
  public mountebankPort: number = 2525; // Mountebank controller runs on this port
  public gatePort: number = 18084; // Gate running on this port
  public imposterPort: number = 8084; // port Deck will send requests to; Mountebank will insert an imposter here
  public onStdOut = (_data: string) => {};
  public onStdErr = (_data: string) => {};
  public onClose = (_code: number) => {};
}

export class MountebankServiceBuilder {
  private options: MountebankServiceOptions = new MountebankServiceOptions();

  mountebankPath(p: string): MountebankServiceBuilder {
    this.options.mountebankPath = p;
    return this;
  }

  mountebankPort(p: number): MountebankServiceBuilder {
    this.options.mountebankPort = p;
    return this;
  }

  gatePort(p: number): MountebankServiceBuilder {
    this.options.gatePort = p;
    return this;
  }

  imposterPort(p: number): MountebankServiceBuilder {
    this.options.imposterPort = p;
    return this;
  }

  onStdOut(fn: (data: string) => void) {
    this.options.onStdOut = fn;
    return this;
  }

  onStdErr(fn: (data: string) => void) {
    this.options.onStdErr = fn;
    return this;
  }

  onClose(fn: (code: number) => void) {
    this.options.onClose = fn;
    return this;
  }

  build(): MountebankService {
    return new MountebankService(this.options);
  }
}
