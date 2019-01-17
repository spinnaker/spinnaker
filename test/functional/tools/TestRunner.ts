import * as process from 'process';
import * as path from 'path';
import { exec, ChildProcess } from 'child_process';
import { MountebankService } from './MountebankService';
import { FixtureService } from './FixtureService';
import { StaticServer } from './StaticServer';

const COPY_FLAGS_TO_WDIO = [
  'replay-fixtures',
  'record-fixtures',
  'mountebank-port',
  'gate-port',
  'imposter-port',
  'browser',
  'headless',
  'savelogs',
];

const TEST_RUNNER_TIMEOUT_MS = 600000; // 10 minutes

export class TestRunner {
  private mountebankService: MountebankService;
  private staticServer: StaticServer;
  private wdioProc: ChildProcess;
  private fixtureService = new FixtureService();
  private launchReadinessPromises: Promise<any>[] = [];

  constructor(private repoRoot: string) {}

  public launchMockServer(gatePort: number, imposterPort: number) {
    console.log('launching mock Gate server');
    this.mountebankService = MountebankService.builder()
      .mountebankPath(path.resolve(this.repoRoot, './node_modules/.bin/mb'))
      .mountebankPort(2525)
      .imposterPort(imposterPort)
      .gatePort(gatePort)
      .onStdErr(str => {
        console.log('mountebank error: ' + str);
        process.exit(2);
      })
      .onClose(code => {
        console.log('mountebank closed with exit code ' + code);
      })
      .build();
    this.launchReadinessPromises.push(this.mountebankService.launchServer());
  }

  public launchStaticServer() {
    console.log('building and serving production version of deck; this takes several minutes');
    this.staticServer = new StaticServer(this.repoRoot);
    this.launchReadinessPromises.push(this.staticServer.launch());
  }

  public run(flags: { [key: string]: number | string | string[] | boolean }) {
    Promise.all(this.launchReadinessPromises)
      .then(() => {
        const cmd = this.buildWebdriverIoCommand(flags);
        console.log(`$ ${cmd}`);
        this.wdioProc = exec(cmd, { cwd: this.repoRoot, timeout: TEST_RUNNER_TIMEOUT_MS }, (err, _stdout, _stderr) => {
          this.cleanUp();
          if (err) {
            console.log(`webdriver.io exited with error: ${err}`);
            process.exit(4);
          }
          process.exit(0);
        });
        this.wdioProc.stdout.on('data', data => {
          process.stdout.write(data);
        });
        this.wdioProc.stderr.on('data', data => {
          process.stderr.write(data);
        });
      })
      .catch(err => {
        console.error(`couldnt start webdriver.io: ${err}`);
        this.cleanUp();
        process.exit(3);
      });
  }

  public serveFixture(testpath: string) {
    console.log(`serving fixture for test ${testpath}`);
    const fixturePath = this.fixtureService.fixturePathForTestPath(testpath);
    Promise.all(this.launchReadinessPromises)
      .then(() => {
        return this.mountebankService.createImposterFromFixtureFile(fixturePath);
      })
      .catch(err => {
        console.error(`error serving fixture: ${err}`);
        this.cleanUp();
        process.exit(6);
      });
  }

  public cleanUp() {
    if (this.mountebankService) {
      console.log('killing mountebank');
      this.mountebankService.kill();
      this.mountebankService = null;
    }
    if (this.staticServer) {
      console.log('killing static server');
      this.staticServer.kill();
      this.staticServer = null;
    }
  }

  private buildWebdriverIoCommand(flags: { [key: string]: number | string | string[] | boolean }) {
    let command: string[] = ['node_modules/.bin/wdio wdio.conf.js'];
    Object.keys(flags).forEach(flag => {
      if (COPY_FLAGS_TO_WDIO.includes(flag)) {
        const value = flags[flag];
        if (typeof value === 'boolean' && value === true) {
          command.push(`--${flag}`);
        } else if (typeof value === 'string' && value !== '') {
          command.push(`--${flag} ${value}`);
        } else if (typeof value === 'number') {
          command.push(`--${flag} ${value}`);
        }
      }
    });
    if (flags['--']) command = command.concat(flags['--'] as string[]);
    return command.join(' ');
  }
}
