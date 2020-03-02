# Deck Functional Test Suite

Run functional tests against Deck without the need for any of Spinnaker's other services
to be running.

## Running Tests

From deck project root, run `yarn functional`.

This will build deck into `/build/webpack` (using `yarn build`).
Then it installs and launches cypress from `/test/functional` (using `yarn test`).

## Writing Tests

Cypress has extensive documentation on their test writing API.
If you are unfamiliar with Cypress, begin by reading the Overview and Getting Started [Guides](https://docs.cypress.io/guides).

### Assertions

Cypress embeds [Mocha](https://mochajs.org/) (test runner) and [Chai](https://www.chaijs.com/) (assertion library).
Because of this, tests read differently than Deck's unit tests (which uses Karma and Jasmine).

### Fixtures

Fixtures are pre-recorded request/response mappings that are use to emulate a working backend API (Gate).
A helper method `registerDefaultFixtures()` will register a baseline of pre-recorded fixtures (mostly empty).

Writing a new functional test requires recording the network request/responses so they can be played back when running the test.

Record the request/responses of a workflow using a tool of your choice.
Save the necessary responses as fixtures in `/fixtures/<provider>/<test_name>/<fixture_name>.json`.
Follow the examples for replaying request/response fixtures in a test.
TL;DR:

```
cy.route('/credentials', 'fixture:core/account_list/credentials.json');
```

_Warning_: Be careful not to include any sensitive information in any saved fixtures.

### Debugging

After building deck (using `yarn build`), run `yarn start` to launch the interactive test runner.
Click the "Run all specs" button.
Your tests are run one at a time, and the browser output is shown in an iframe.

#### Running only some tests

To run a single test only, you can click that test only (instead of "Run all specs").
Alternatively, you can run specific tests (`it()`) by using [Mocha's `only` mechanism](https://mochajs.org/#exclusive-tests).
To use this, change a `describe()` or `it()` block to `describe.only()` or `it.only()`.

#### Test results

Individual steps for each test is shown on the left.
If the step is selecting a DOM element, the number of selected elements (and visible/hidden status) is shown next to the step.
As you move your mouse over each step, a DOM snapshot from that point in time is shown on the right.

#### Debugger

Utilize the javascript debugger!
You can use the debugger as your tests run, adding breakpoints, etc.
If you click on a step in a cypress test (successful or not), detailed information is output to the javascript console.
