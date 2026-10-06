import { expect } from 'vitest';

// Jasmine's toHaveSize: length for array/string, size for Map/Set, key count for objects.
function sizeOf(value: unknown): number | null {
  if (value == null) return null;
  if (typeof (value as { length?: number }).length === 'number') return (value as { length: number }).length;
  if (value instanceof Map || value instanceof Set) return value.size;
  if (typeof value === 'object') return Object.keys(value as object).length;
  return null;
}

// Jasmine's toHaveBeenCalledBefore: uses spy invocation order.
function firstOrder(mock: { mock?: { invocationCallOrder?: number[] } }): number | null {
  const orders = mock?.mock?.invocationCallOrder;
  return orders && orders.length ? orders[0] : null;
}

export function registerVitestCompat(): void {
  expect.extend({
    toHaveSize(received: unknown, expected: number) {
      const size = sizeOf(received);
      const pass = size === expected;
      return {
        pass,
        message: () =>
          `expected ${this.utils.printReceived(received)} to have size ${this.utils.printExpected(
            expected,
          )} but got ${this.utils.printReceived(size)}`,
      };
    },
    toHaveBeenCalledBefore(received: unknown, other: unknown) {
      const a = firstOrder(received as never);
      const b = firstOrder(other as never);
      const pass = a != null && b != null && a < b;
      return {
        pass,
        message: () =>
          `expected spy to have been called before other spy (first calls: ${this.utils.printReceived(
            a,
          )} vs ${this.utils.printExpected(b)})`,
      };
    },
  });

  // Jasmine's global fail(): usable in both statement and expression positions.
  (globalThis as Record<string, unknown>).fail = (message?: string | Error): never => {
    if (message instanceof Error) throw message;
    throw new Error(message ?? 'fail');
  };

  // Jasmine's global expectAsync, mapped onto Vitest resolves/rejects semantics.
  (globalThis as Record<string, unknown>).expectAsync = (promise: Promise<unknown>) => ({
    async toBeResolved() {
      let resolved = false;
      try {
        await promise;
        resolved = true;
      } catch {
        resolved = false;
      }
      expect(resolved).toBe(true);
    },
    async toBeResolvedTo(value: unknown) {
      await expect(promise).resolves.toEqual(value);
    },
    async toBeRejected() {
      let rejected = false;
      try {
        await promise;
      } catch {
        rejected = true;
      }
      expect(rejected).toBe(true);
    },
    async toBeRejectedWith(value: unknown) {
      let error: unknown;
      let rejected = false;
      try {
        await promise;
      } catch (e) {
        rejected = true;
        error = e;
      }
      expect(rejected).toBe(true);
      expect(error).toEqual(value);
    },
    async toBeRejectedWithError(expected?: unknown, message?: string | RegExp) {
      if (typeof expected === 'function') {
        await expect(promise).rejects.toThrow(expected as never);
        if (message != null) {
          await expect(promise).rejects.toThrow(message);
        }
      } else if (expected != null) {
        await expect(promise).rejects.toThrow(expected as string | RegExp);
      } else {
        await expect(promise).rejects.toThrow();
      }
    },
  });
}
