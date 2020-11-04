# Amazon ECS Integration tests

Tests which exercise Amazon ECS controllers and atomic operations against a running `clouddriver` application.

## Running the tests

To manually run the gradle task for these tests:
```bash
$> ./gradlew :clouddriver-ecs:integrationTest
```

## Guidance for modifying this package

### When to add a new test

New Amazon ECS provider features of significant scope should include an integration test which exercises new functionality.
Examples of qualifying changes include (but are not limited to):

* Implementing a new atomic operation
* New forking logic in how `CreateServerGroup` functions or is validated (especially re: broadly impactful settings like `networkMode`, launch type, load balancing, or application autoscaling)
* Adding a new controller for a new type of resource

### Changing existing tests

In general, existing test cases should function as-is after new contributions to ensure existing features continue to function as expected.
Possible exceptions to this guidance may include:

* Updates to internal implementation details (required `@Beans`, etc.) that don't effect operation success or API response content
* Adding and asserting on *new* data in a `clouddriver` API response
