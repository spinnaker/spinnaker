# Deck Functional Test Suite

Run functional tests against Deck without the need for any of Spinnaker's other services
to be running.

The test runner script is called as follows from the deck repository root:

```
test/functional/run.js [options] -- [webdriver.io options]
```

The runner script performs the following work:

1. Parses command line arguments
2. Fires up a Mountebank mock server if network fixtures are to be recorded or replayed
3. Compiles and serves the production build of Deck
4. Delegates to webdriver.io to execute tests
5. Cleans up all the processes described above

The options available for the runner script are as follows:

```
--serve-deck           Boolean. Build and test the production version of Deck.
--replay-fixtures      Boolean. Use pre-recorded network fixtures in place of a
                       running Gate server.
--record-fixtures      Boolean. Record network fixtures for later replay.
                       Requires a running Spinnaker deployment.
--serve-fixture        Boolean. Serve a pre-recorded network fixture in place
                       of a running Gate server. Used during development to
                       allow debugging of fixture responses.
--gate-port            (only used when recording fixtures) Port on which a
                       real Gate server is currently running. The imposter
                       recording a test's network fixture will proxy requests
                       from Deck to this port and record the results.
--imposter-port        (only used when recording or replaying fixtures)
                       Port on which the imposter Gate server should be created.
                       This should typically be the port that Gate normally
                       runs on (8084).
--browser              Either "chrome" or "firefox".
--headless             Boolean. Run the browser in headless mode.
--savelogs             Chrome-only. Save browser's console log to file named
                       [selenium-session-id].browser.log.
--help                 Print the script's help information.
```

Any arguments appearing after `--` will be passed to webdriver.io. To see webdriver.io's
cli args, simply run `test/functional/run.js -- --help`

## Writing New Tests Requires Gate To Run On A Different Port

Writing a new functional test requires recording an accompanying network
fixture so that the test is reproducible.

In order to record a fixture the test runner script creates a Gate imposter server
that proxies requests from Deck to Gate, recording the traffic.

Since the imposter server needs to sit between Deck and Gate to record traffic,
it needs to use Gate's port. Therefore, Gate must be moved to a different port
for the imposter to work.

See "Recording a Network Fixture For a New Test" in the Example Usage section below.

## Warning: Record Network Fixtures In Safe Environments!

If you decide to create a new test and network fixture, please bear in mind
that _all_ of the network traffic between Deck and Gate will be recorded. This
will include any authentication headers exchanged during communication as well
as all data pertaining to the infrastructure configured in your Spinnaker.

It's strongly advised that network fixtures are recorded with any authentication
system disabled and with any sensitive data removed from your working Spinnaker.
A simple way to do this is to create a fresh Spinnaker deployment with only the
data required to successfully complete the functional test, and then perform the
recording step using that deployment.

## Example Usage:

### Running Tests In Headless Chrome Using Network Fixtures

```
test/functional/run.js --serve-deck \
  --replay-fixtures \
  --browser chrome
  --headless
```

This will build and test the production version of Deck.

### Recording a Network Fixture For a New Test

First, move Gate off of its normal port. Assuming that you're using Halyard this
can be achieved by opening `~/.hal/default/service-settings/gate.yml` and adding
the following line:

```
port: 18084
```

Second, restart Gate:

```bash
hal deploy apply --service-names gate
```

Third, run the test runner script. Supply the necessary port information for Gate
and for the Gate imposter. Further, add webdriver.io's --spec flag to only
execute the single test you want to record a fixture for.

```bash
test/functional/run.js \
  --serve-deck \
  --record-fixtures \
  --gate-port 18084 \
  --imposter-port 8084 \
  -- --spec path/to/test.spec.ts
```

After this test has run successfully a fixture will have been written to the
following location:

```
path/to/test.spec.ts.mountebank_fixture.json
```

Review the fixture to ensure that no sensitive information has been captured
during the recording.
