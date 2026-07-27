export interface PromiseService {
  all<T extends readonly unknown[]>(values: T): Promise<{ -readonly [K in keyof T]: Awaited<T[K]> }>;
  all<T extends Record<string, unknown>>(values: T): Promise<{ [K in keyof T]: Awaited<T[K]> }>;
  reject<T = never>(reason?: unknown): Promise<T>;
  resolve<T = void>(value?: T | PromiseLike<T>): Promise<Awaited<T>>;
}

function all<T extends readonly unknown[]>(values: T): Promise<{ -readonly [K in keyof T]: Awaited<T[K]> }>;
function all<T extends Record<string, unknown>>(values: T): Promise<{ [K in keyof T]: Awaited<T[K]> }>;
function all(
  values: ReadonlyArray<unknown | PromiseLike<unknown>> | Record<string, unknown>,
): Promise<unknown[] | Record<string, unknown>> {
  if (Array.isArray(values)) {
    return Promise.all(values);
  }

  const entries = Object.entries(values);
  return Promise.all(entries.map(([, value]) => value)).then((resolved) =>
    Object.fromEntries(entries.map(([key], index) => [key, resolved[index]])),
  );
}

export function createNativePromiseService(): PromiseService {
  return {
    all,
    reject: <T = never>(reason?: unknown) => Promise.reject<T>(reason),
    resolve: <T = void>(value?: T | PromiseLike<T>) => Promise.resolve(value) as Promise<Awaited<T>>,
  };
}

export const nativePromiseService = createNativePromiseService();
