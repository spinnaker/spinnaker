# Deck Functional Tests

## Recording Network Fixtures

Usage of fixtures goes as follows:

1. Create a mountebank control server. For now this is managed manually but will soon be coordinated by a script:

```
$ node
> require('ts-node/register');
{}
> const { MountebankService } = require('./test/functional/tools/MountebankService.ts');
undefined
> MountebankService.builder().
...   mountebankPath(process.cwd() + '/node_modules/.bin/mb').
...   onStdOut(data => { console.log('mb stdout: ' + String(data)); }).
...   onStdErr(data => { console.log('mb stderr: ' + String(data)); }).
...   build().launchServer();
Promise {
  <pending>,
  domain:
   Domain {
     domain: null,
     _events: { error: [Function: debugDomainError] },
     _eventsCount: 1,
     _maxListeners: undefined,
     members: [] } }
> mb stdout: info: [mb:2525] mountebank v1.15.0 now taking orders - point your browser to http://localhost:2525 for help
```

2. Launch Gate on a different port. We want 8084 to be free for the mitm proxy that will record the network traffic. Open `~/.hal/default/service-settings/gate.yml` and add these contents:

```
port: 18084
```

3. Restart Gate:

```
hal deploy apply --service-names gate
```

4. Record a fixture for a specific test:

```
$ ./node_modules/.bin/wdio wdio.conf.js --record-fixtures --spec test/functional/tests/core/home.spec.ts

DEPRECATION: Setting specFilter directly on Env is deprecated, please use the specFilter option in `configure`
DEPRECATION: Setting stopOnSpecFailure directly is deprecated, please use the failFast option in `configure`
․wrote fixture to ~/dev/spinnaker/deck/test/functional/tests/core/home.spec.ts.mountebank_fixture.json


1 passing (5.60s)
```

5. Kill the Gate process. On Mac this would go something like:

```
kill -15 $(lsof -t -i tcp:18084)
```

6. Run the test again without Gate running, instructing the test runner to create a network imposter:

```
$ ./node_modules/.bin/wdio wdio.conf.js --replay-fixtures --spec test/functional/tests/core/home.spec.ts

DEPRECATION: Setting specFilter directly on Env is deprecated, please use the specFilter option in `configure`
DEPRECATION: Setting stopOnSpecFailure directly is deprecated, please use the failFast option in `configure`
Creating imposter from fixture file ~/dev/spinnaker/deck/test/functional/tests/core/home.spec.ts.mountebank_fixture.json
․

1 passing (6.00s)
```

The mountebank server will still be running on port 2525 but can easily be exited by calling:

```
kill -15 $(lsof -t -i tcp:2525)
```

## Downloading Pre-existing Network Fixtures

The section above describes recording and replaying new network fixtures. Since the fixtures can be quite large
they are not committed alongside the test code in this repo. Instead they can be served from a bucket and fetched
prior to test execution. A GCS fixture downloader is included but downloaders for other services can be easily
added.

To download existing fixtures from a GCS bucket use the FixtureService. This process is manual currently but
a script to automate this process will be added soon:

1. Instantiate the FixtureService with the fully qualified base URI for the GCS bucket and the root of the test specs:

```
$ node
> require('ts-node/register');
undefined
> const { FixtureService } = require('./test/functional/tools/FixtureService.ts');
undefined
> const fixtureService = new FixtureService('gs://my-bucket/my-network-fixtures', 'test/functional/tests');
undefined
```

2. Get the list of missing fixture files. In this example the fixture for `core/home.spec.ts` is missing.

```
> const missingFixtures = fixtureService.findMissingFixtures();
undefined
> missingFixtures
[ 'test/functional/tests/core/home.spec.ts.mountebank_fixture.json' ]
```

3. Download the missing fixtures:

```
> fixtureService.downloadFixtures(missingFixtures).
... then(() => { console.log('Fetched all fixtures'); }).
... catch(err => { console.log('Error fetching fixtures: ' + err); });
fetching fixture for test/functional/tests/core/home.spec.ts.mountebank_fixture.json
Promise {
  <pending>,
  domain:
   Domain {
     domain: null,
     _events: { error: [Function: debugDomainError] },
     _eventsCount: 1,
     _maxListeners: undefined,
     members: [] } }
Copying gs://sbws/deck-functional-tests/fixtures/core/home.spec.ts.mountebank_fixture.json...
/ [1 files][152.2 KiB/152.2 KiB]
Operation completed over 1 objects/152.2 KiB.
Fetched all fixtures
```

Note: The fully qualified base URI to the GCS bucket is expected to point at a GCS folder matching
the directory structure of the `test/functional/tests` directory. At time of writing this means a structure
like this:

```
.
├── core
│   ├── home.spec.ts.mountebank_fixture.json
└── google
    ├── clone.spec.ts.mountebank_fixture.json
```

## Running a Static Server With a Built Version of Deck

It can be preferable to run functional tests against a production build of Deck. However Deck does not come with
a webserver to serve the compiled application. The functional test suite includes a simple static server for
this purpose.

To serve the built version of deck:

1. Build Deck

```
yarn build
```

2. Serve Deck. For now this is managed manually but will soon be coordinated by a script:

```
$ node
> require('ts-node/register');
{}
> const { StaticServer } = require('./test/functional/tools/StaticServer.ts');
undefined
> const server = new StaticServer('build/webpack', 9000);
undefined
> server.launch().then(() => console.log('ready!')).catch(err => console.log('error launching static server: ' + err));
Promise {
  <pending>,
  domain:
   Domain {
     domain: null,
     _events: { error: [Function: debugDomainError] },
     _eventsCount: 1,
     _maxListeners: undefined,
     members: [] } }
> ready!
```

3. Once tests have completed the server can be shut down by calling:

```
> server.kill().then(() => console.log('done'));
Promise {
  <pending>,
  domain:
   Domain {
     domain: null,
     _events: { error: [Function: debugDomainError] },
     _eventsCount: 1,
     _maxListeners: undefined,
     members: [] } }
> done
```
