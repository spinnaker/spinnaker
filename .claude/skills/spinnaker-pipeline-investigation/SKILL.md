---
name: spinnaker-pipeline-investigation
description: |
  Use when given a Spinnaker pipeline execution URL or ID that has failed or
  appears stuck and you need to determine root cause. Use when asked to
  "investigate why a pipeline failed", "why is this pipeline stuck",
  or given a URL like https://api-spinny.moderne.ninja/pipelines/<id>.
allowed-tools:
  - Bash
  - Read
---

# Spinnaker Pipeline Investigation

## Prerequisites

- AWS CLI configured with `labs` account credentials
- SSM Session Manager access (`ssm:StartSession`) to the Spinnaker EC2 instance
- Python 3 available via `uv run python3` (for the PTY wrapper)

## Overview

The Gate API (`api-spinny.moderne.ninja`) requires Google OAuth — you cannot
`curl` it directly. All useful signal is in Orca's journald logs on the
Spinnaker EC2 instance. SSM in, grep, filter noise, trace the chain.

## Step 1: Find the Spinnaker instance

```bash
aws ec2 describe-instances \
  --filters Name=tag:Name,Values=spinnaker Name=tag:Environment,Values=labs \
  --query 'Reservations[].Instances[].InstanceId' --output text
```

SSM in using the PTY wrapper (see the ec2-ssm-commands skill in
`~/Work/claude-share/`).

## Step 2: First pass — what happened?

```bash
sudo journalctl -u orca --since "24 hours ago" --no-pager \
  | grep "<EXECUTION_ID>" | grep -v "Upserting" | head -100
```

If that returns nothing, widen to `--since "48 hours ago"` or check whether
the execution is on a different service (orca logs all pipelines together).

## Step 3: Filter noise to expose lifecycle events

```bash
sudo journalctl -u orca --since "24 hours ago" --no-pager \
  | grep "<EXECUTION_ID>" \
  | grep -v "Upserting\|Re-queuing CompleteExecution\|Received message CompleteExecution\|Received message RunTask.*MonitorPipeline\|Received message RunTask.*SuspendExecution\|Received message StartTask\|Received message StartStage\|Received message StartExecution" \
  | head -80
```

Key events to look for:

| Log fragment | Meaning |
|---|---|
| `DependentPipelineStarter: executing dependent pipeline <ID>` | Started as a child of another pipeline |
| `ContinueParentStage(phase=STAGE_BEFORE)` | Pre-stage phase completed; main tasks now starting |
| `CompleteTask(status=SUCCEEDED)` | Stage task completed OK |
| `CompleteTask(status=STOPPED, originalStatus=TERMINAL)` | Stage failed terminally |
| `TimeoutException: MonitorPipelineTask of stage X timed out` | Parent gave up waiting for child |
| `completed pipeline execution size: ...pipelineName=X` | Execution finished (note the pipeline name) |

## Step 4: Distinguish stuck vs failed

**Stuck (still running):**
- `Re-queuing CompleteExecution` in recent logs at high attempt count → execution is alive but stages haven't all finished
- `RunTask.*MonitorPipelineTask` at high attempt count → a Pipeline stage is waiting on a child execution

**Actually finished:**
- `CompleteTask(status=STOPPED, originalStatus=TERMINAL)` with no recent activity
- `TimeoutException` in a `CancelStage` log line
- `completed pipeline execution size:` line present

## Step 5: Trace the execution chain

For a `MonitorPipelineTask` that timed out or is stuck, find what child it launched.
Narrow to the window when `StartPipelineTask` ran for that stage:

```bash
sudo journalctl -u orca \
  --since "YYYY-MM-DD HH:MM:00" --until "YYYY-MM-DD HH:MM:59" \
  --no-pager \
  | grep "StartPipelineTask\|executing dependent pipeline\|triggering dependent pipeline" \
  | grep -v "Received message\|Upserting"
```

Look for the pair that fires within the same second:
```
triggering dependent pipeline <CONFIG_UUID>
executing dependent pipeline <CHILD_EXECUTION_ID>   ← trace this next
```

Repeat Steps 2–4 for each child execution ID until you find the root failure.

## Step 6: Time window failures

If a child is stuck in `SuspendExecutionDuringTimeWindowTask` at high attempt
count, it's waiting for its deploy window. Get the window spec from the log
context near the timeout:

```bash
sudo journalctl -u orca --since "..." --no-pager \
  | grep "<EXECUTION_ID>" | grep "whitelist\|restrictedExecutionWindow" | head -5
```

**Critical: window hours are in PDT (UTC-7), not UTC.**

`startHour: 16, endHour: 20` means 16:00–20:00 PDT = 23:00–03:00 UTC.

Also check for `jitter.maxDelay` — up to 600 s (10 min) of random delay is
added even when already inside the window, which can push the actual trigger
past the window boundary.

## Step 7: Build Spinnaker Deck links

For sharing or UI inspection (requires Google SSO in browser):
```
https://spinny.moderne.ninja/#/applications/<APP_NAME>/executions/details/<EXECUTION_ID>
```

The app name is logged as `application=<name>` throughout the Orca output.

## Common failure modes

| Symptom | Root cause | Fix |
|---|---|---|
| `MonitorPipelineTask timed out after 12 hours` | Child missed its deploy window; next window is >12h away | Increase `stageTimeoutMs` in `runPipeline()` in `spinnaker.libsonnet` |
| Child stuck in `SuspendExecutionDuringTimeWindowTask` for hours | Triggered outside deploy window (check jitter — up to 10 min) | Trigger `deploy-all-fanout` earlier in the window |
| Stage `TERMINAL` immediately | Clouddriver / Front50 call failure | Check logs near `CompleteTask(TERMINAL)` for the exception |
| `StartPipelineTask.getPipelineById` exception in stack trace | Intermittent Front50 lookup failure; usually self-heals on retry | Check if pipeline eventually succeeded; if not, check Front50 health |

## Spinnaker source files (moderne-saas)

Pipeline configs are generated from Jsonnet — never edit the `generated/` JSON directly:

- **Stage timeouts / windows:** `infra/spinnaker/lib/spinnaker.libsonnet` → `runPipeline()`
- **Tenant deploy windows:** `infra/tenants/<name>.yaml` → `releaseWindow`
- **Regenerate + sync:** `mise exec java@21 -- ./gradlew :infra:syncPipelines`
