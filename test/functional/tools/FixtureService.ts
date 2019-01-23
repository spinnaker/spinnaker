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

  public anonymousAuthFixturePath(): string {
    return path.resolve(__dirname, '../fixtures/anonymous_auth_response.mountebank_fixture.json');
  }
}
