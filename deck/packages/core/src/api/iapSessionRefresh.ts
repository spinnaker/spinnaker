import { SETTINGS } from '../config/settings';

export const IAP_SESSION_REFRESH_URL = '/_gcp_iap/do_session_refresh';

export interface IIapRequestConfig {
  url?: string;
}

export interface IIapSessionRequestState {
  generation: number;
  refreshAttempted: boolean;
}

interface IIapRefreshInFlight {
  promise: Promise<void>;
}

let successfulGeneration = 0;
let refreshInFlight: IIapRefreshInFlight = null;
let requestStates = new WeakMap<object, IIapSessionRequestState>();

export function resetIapSessionRefreshState(): void {
  successfulGeneration = 0;
  refreshInFlight = null;
  requestStates = new WeakMap<object, IIapSessionRequestState>();
}

export function captureIapSessionRefreshGeneration(): number {
  return successfulGeneration;
}

export function hasIapSessionRefreshedSince(generation: number): boolean {
  return generation < successfulGeneration;
}

export function getIapSessionRequestState(request: object): IIapSessionRequestState {
  let state = requestStates.get(request);
  if (!state) {
    state = { generation: captureIapSessionRefreshGeneration(), refreshAttempted: false };
    requestStates.set(request, state);
  }
  return state;
}

export function markIapSessionRefreshAttempted(request: object): void {
  requestStates.set(request, { generation: captureIapSessionRefreshGeneration(), refreshAttempted: true });
}

export function shouldRefreshIapSession(status: number, config: IIapRequestConfig, refreshAttempted = false): boolean {
  return (
    status === 401 && SETTINGS.feature.iapRefresherEnabled && !refreshAttempted && !isIapSessionRefreshUrl(config.url)
  );
}

export function refreshIapSession(refreshSession: () => PromiseLike<unknown>): Promise<void> {
  if (!refreshInFlight) {
    let refresh: Promise<void>;
    try {
      refresh = Promise.resolve(refreshSession()).then(() => undefined);
    } catch (error) {
      refresh = Promise.reject(error);
    }

    const trackedRefresh: IIapRefreshInFlight = { promise: null };
    trackedRefresh.promise = refresh
      .then(() => {
        if (refreshInFlight === trackedRefresh) {
          successfulGeneration += 1;
        }
      })
      .finally(() => {
        if (refreshInFlight === trackedRefresh) {
          refreshInFlight = null;
        }
      });
    refreshInFlight = trackedRefresh;
  }

  return refreshInFlight.promise;
}

function isIapSessionRefreshUrl(url?: string): boolean {
  if (!url) {
    return false;
  }

  try {
    return new URL(url, 'http://localhost').pathname === IAP_SESSION_REFRESH_URL;
  } catch {
    return false;
  }
}
