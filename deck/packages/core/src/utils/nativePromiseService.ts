import type { IQService } from 'angular';

import { createDeferred } from './deferred';

function all<T>(values: Array<T | PromiseLike<T>>): Promise<T[]>;
function all<T extends Record<string, unknown>>(values: T): Promise<{ [K in keyof T]: Awaited<T[K]> }>;
function all(
  values: Array<unknown | PromiseLike<unknown>> | Record<string, unknown>,
): Promise<unknown[] | Record<string, unknown>> {
  if (Array.isArray(values)) {
    return Promise.all(values);
  }

  const entries = Object.entries(values);
  return Promise.all(entries.map(([, value]) => value)).then((resolved) =>
    Object.fromEntries(entries.map(([key], index) => [key, resolved[index]])),
  );
}

export function createNativePromiseService(): IQService {
  return Object.assign(
    ((<T>(resolver: (resolve: (value: T | PromiseLike<T>) => void, reject: (reason?: unknown) => void) => void) =>
      new Promise<T>(resolver)) as unknown) as IQService,
    {
      defer: <T>() => ({ ...createDeferred<T>(), notify: (): void => undefined }),
      all,
      reject: (reason?: unknown) => Promise.reject(reason),
      resolve: (value?: unknown) => Promise.resolve(value),
      when: (value: unknown) => Promise.resolve(value),
    },
  ) as IQService;
}

export const nativePromiseService = createNativePromiseService();
