import * as path from 'path';
import * as url from 'url';
import * as fs from 'fs';
import { GCSFixtureDownloader } from './GCSFixtureDownloader';

export class FixtureService {
  constructor(private sourceUri: string, private specsRoot: string) {}

  private fetchFixture(toFile: string): Promise<any> {
    const { protocol } = url.parse(this.sourceUri);
    if (protocol === 'gs:') {
      return GCSFixtureDownloader.download(this.sourceUri, this.specsRoot, toFile);
    } else {
      return Promise.reject(new Error(`unsupported fixture source uri: ${this.sourceUri}`));
    }
  }

  public findMissingFixtures(): string[] {
    const queue = [this.specsRoot];
    const missingFixtures: string[] = [];
    while (queue.length > 0) {
      const dir = queue.shift();
      const entries = fs.readdirSync(dir);
      entries.forEach((entry: string) => {
        const fullpath = path.join(dir, entry);
        const stat = fs.lstatSync(fullpath);
        if (stat.isDirectory() && !stat.isSymbolicLink()) {
          queue.push(fullpath);
        } else if (entry.endsWith('.spec.ts')) {
          const fixtureFile = this.fixtureNameForTestPath(fullpath);
          if (!entries.includes(fixtureFile)) {
            missingFixtures.push(path.join(dir, fixtureFile));
          }
        }
      });
    }
    return missingFixtures;
  }

  public downloadFixtures(fixturePaths: string[]): Promise<void> {
    fixturePaths = fixturePaths.slice(0);
    const downloadRemainingFixtures: () => Promise<any> = () => {
      if (fixturePaths.length > 0) {
        const fixturePath = fixturePaths.pop();
        console.log('fetching fixture for ' + fixturePath);
        return this.fetchFixture(fixturePath).then(downloadRemainingFixtures);
      }
      return Promise.resolve();
    };
    return downloadRemainingFixtures();
  }

  public fixtureNameForTestPath(testpath: string) {
    const basename = path.basename(testpath);
    return basename + '.mountebank_fixture.json';
  }

  public fixturePathForTestPath(testpath: string) {
    return path.join(path.dirname(testpath), this.fixtureNameForTestPath(testpath));
  }
}
