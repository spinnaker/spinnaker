# AWS EC2 Integration tests

## Running the tests

To manually run the gradle task:
```bash
$> ./gradlew :clouddriver-aws:integrationTest
```

## Guidance for modifying this package

### When to add a new test

Existing or new AWS EC2 provider features of significant scope should include integration tests.

Examples of qualifying changes include (but are not limited to):
* Changes to validations
* New or significant changes to an existing atomic operation
* Addition of new AWS launch templates features
* Addition of new AWS Autoscaling features
* Complex refactoring of code areas not included in integration tests

### Changing existing tests

In general, existing test cases should function as-is after new contributions to ensure existing features continue to function as expected.
Possible exceptions to this guidance may include:

* Updates to internal implementation details (required `@Beans`, etc.) that don't effect operation success or API response content
* Adding and asserting on *new* data in a `clouddriver` API response
