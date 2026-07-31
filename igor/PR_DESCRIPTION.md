# GitLab CI: Add pipeline trigger, cancel, and StoppableBuildService interface

## Summary

This PR adds native pipeline trigger and cancel support for GitLab CI in Igor, and introduces a `StoppableBuildService` interface to decouple build cancellation from Jenkins-specific code.

Currently, cancelling a running CI build in Spinnaker only works for Jenkins (`BuildController.stopJob()` checks `instanceof JenkinsService`). This change makes cancellation extensible to any CI provider by introducing a common interface.

## Changes

### New: `StoppableBuildService` interface (`igor-core`)

```java
public interface StoppableBuildService {
    void stopRunningBuild(String jobName, long buildNumber);
    default void stopQueuedBuild(String queuedId) { /* throws UnsupportedOperationException */ }
}
```

Any CI service that supports stopping/cancelling builds implements this interface. The `stopQueuedBuild` method has a default implementation since not all providers support queue cancellation.

### GitLab CI: Trigger and Cancel (`igor-web`)

- **`GitlabCiClient.java`** — Added `triggerPipeline()` and `cancelPipeline()` Retrofit endpoints
- **`GitlabCiProperties.java`** — Added `triggerToken` configuration field to `GitlabCiHost`
- **`GitlabCiService.java`** — Implemented `triggerBuildWithParameters()` (was `UnsupportedOperationException`) and added `cancelPipeline()`. Implements `StoppableBuildService`.

### BuildController: Generic stop logic

- **`BuildController.groovy`** — `stopJob()` now uses `instanceof StoppableBuildService` instead of `instanceof JenkinsService`. Removed Groovy `metaClass.respondsTo` reflection in favor of direct interface method calls.

### JenkinsService: Backward compatibility

- **`JenkinsService.java`** — Implements `StoppableBuildService` with bridge methods to existing `stopRunningBuild(String, Long)` and `stopQueuedBuild(String)` methods. No behavior change for Jenkins users.

## Configuration

To enable GitLab CI pipeline triggering, add `triggerToken` to the master config:

```yaml
gitlab-ci:
  enabled: true
  masters:
    - name: my-gitlab
      address: https://gitlab.example.com
      privateToken: <api-token>
      triggerToken: <pipeline-trigger-token>
```

The `triggerToken` is a [GitLab Pipeline Trigger Token](https://docs.gitlab.com/ee/ci/triggers/) created in the GitLab project settings. It is stored server-side in Igor's configuration — never exposed in pipeline JSON.

## Testing

- **`GitlabCiServiceSpec`** — 5 new test cases: trigger happy path, default ref, missing token validation, cancel, stopRunningBuild delegation
- **`BuildControllerStopSpec`** — 7 new test cases: stopRunningBuild routing, stopQueuedBuild routing, 404 handling, error propagation, UnsupportedOperationException handling, non-stoppable service rejection, unknown master handling
- All existing tests pass (no regressions)

## Follow-up Work

- **Orca**: Create `GitlabCiStage.java` for a dedicated stage type (currently uses the Jenkins stage type via `CIStage`)
- **Deck**: Add a GitLab CI stage selector with project/ref/variables form fields  
- **Task renaming**: Rename `StartJenkinsJobTask` → `StartBuildJobTask`, `StopJenkinsJobTask` → `StopBuildJobTask` (requires backward compatibility validation with stored pipeline executions)
- **Fiat permissions**: Document configuration for GitLab CI masters

## Related Discussion

Community discussion on task naming and `StoppableBuildService` approach: [link to Spinnaker Slack/GitHub discussion]
