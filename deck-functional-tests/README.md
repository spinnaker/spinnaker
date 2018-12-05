# Functional Tests for Deck

This folder contains functional tests for Deck, includes some tools to help write new ones and
provides a test runner to execute them.

At their simplest the tests execute against your running Spinnaker deployment. Unfortunately
doing so is quite brittle. There's so much configuration and setup involved in a full Spinnaker
deployment that reproducing whatever environment the tests were originally written against would
be a huge undertaking, and probably quite expensive.

The alternative then is to execute these functional tests against pre-recorded network responses.
This increases the burden slightly when writing new tests since the network captures need to
be taken. There's also a risk of "API drift" where the captured network responses no longer reflect
the actual responses produced by Gate. However the payoff is dramatically reduced overhead when
executing the tests.

All that you need to execute the tests is a copy of deck's built files and this test runner will
serve them up, open the browser and start clicking around, replay any needed network responses,
and then check the expected UI state.

## Running the tests

#### Execute the tests against your currently running Spinnaker

```
yarn test
```

#### Execute a single test against your running Spinnaker

```
yarn test --spec ./src/path/to/spec.file.ts
```

#### Execute the tests without a running Spinnaker

This requires fixtures to have been recorded for the test(s) you're running.

```
yarn test --replay-network
```

#### Serve Deck from static files and test against it (i.e. for testing production build)

When you don't have Deck running already, or you don't want to figure out how to spin up a webserver
to serve the app you can use the suite's built-in Express server.

```
yarn test --serve-static /path/to/built/deck/assets/directory
```

#### Record HTTP response fixtures for a single test

This assumes that Gate is running at a fixed port of 18084.

```
yarn test --record --spec ./path/to/spec/file
```

#### Record HTTP response fixtures for every test

This assumes that Gate is running at a fixed port of 18084.

```
yarn test --record
```

## Build and run the local docker container

```bash
# From the deck-functional-tests directory
docker/build-local.sh
docker run --rm -it deck-functional-tests:local
```

Each time you run the local docker image it will execute tests from the image's /deck-functional-tests
directory. By default this will be a checkout of the test suite performed at the time the docker image was
created.

If you're working on tests, either modifying or creating them, it's helpful to mount your local checkout
inside the docker container so that your changes are exercised. To do so, use docker's --volume flag:

```bash
# From your local deck-functional-tests directory
docker run -v $PWD:/deck-functional-tests --rm -it deck-functional-tests:local
```

## Build and run the CI docker container

```bash
# From the deck-functional-tests git checkout root
docker/build-ci.sh
docker run --rm -it deck-functional-tests:ci
```

Each time you run the CI docker image it will fetch the master branch of both the deck and deck-functional-tests
repositories, perform a `yarn` install, build deck for production, and then execute the test suite against
the built assets.

## Screenshots

Screenshots of failed test runs end up in the `./screenshots` directory. NOTE: this feature has been disabled
for the time being because it can cause red-herring exceptions at the end of a test run.

## TODO

- Figure out a way to mitigate API drift in the network responses - contract tests against Gate?
- Customisable Gate port when recording test responses
- Customisable mountebank deployment when recording and replaying test responses
- Customisable deck port when serving and consuming static assets
- Customisable log output: test results, child process stderr + stdout, etc
