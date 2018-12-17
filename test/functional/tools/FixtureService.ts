import * as path from 'path';

export class FixtureService {
  constructor() {}

  public fixtureNameForTestPath(testpath: string) {
    const basename = path.basename(testpath);
    return basename + '.mountebank_fixture.json';
  }

  public fixturePathForTestPath(testpath: string) {
    return path.join(path.dirname(testpath), this.fixtureNameForTestPath(testpath));
  }
}
