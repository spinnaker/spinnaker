import userEvent from '@testing-library/user-event';

type SetupUserOptions = Parameters<typeof userEvent.setup>[0];

/**
 * Creates a `@testing-library/user-event` instance configured with `delay: null`.
 *
 * By default `userEvent.setup()` schedules a real macrotask (`setTimeout`) between each
 * simulated event. Under the parallel Vitest worker pool that per-event timer is easily
 * starved of CPU, which makes interaction-heavy specs run an order of magnitude slower and
 * intermittently exceed the per-test timeout (this was the root cause of the flaky
 * PipelineConfigPage specs). Passing `delay: null` removes the timer round-trip while
 * preserving event semantics.
 *
 * Callers can still override any option, e.g. `setupUser({ advanceTimers: vi.advanceTimersByTime })`
 * for specs that drive fake timers.
 */
export function setupUser(options?: SetupUserOptions): ReturnType<typeof userEvent.setup> {
  return userEvent.setup({ delay: null, ...options });
}
