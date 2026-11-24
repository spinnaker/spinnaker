import { SETTINGS } from '../../config/settings';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'spinnaker.theme';
export const THEME_ATTRIBUTE = 'data-theme';

const PREFERENCES: ThemePreference[] = ['light', 'dark', 'system'];
const DARK_QUERY = '(prefers-color-scheme: dark)';

type Listener = (preference: ThemePreference) => void;
const listeners = new Set<Listener>();

const isPreference = (value: unknown): value is ThemePreference => PREFERENCES.includes(value as ThemePreference);

// Operator-configured fallback (settings.js `defaultTheme`) for users who haven't chosen a theme.
export function getDefaultTheme(): ThemePreference {
  return isPreference(SETTINGS.defaultTheme) ? SETTINGS.defaultTheme : 'light';
}

// localStorage throws in some privacy modes and when storage is disabled; theming must never break the app.
export function getThemePreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isPreference(stored) ? stored : getDefaultTheme();
  } catch {
    return getDefaultTheme();
  }
}

export function resolveTheme(preference: ThemePreference): ResolvedTheme {
  if (preference === 'system') {
    return window.matchMedia?.(DARK_QUERY).matches ? 'dark' : 'light';
  }
  return preference;
}

export function applyTheme(preference: ThemePreference): ResolvedTheme {
  const resolved = resolveTheme(preference);
  document.documentElement.setAttribute(THEME_ATTRIBUTE, resolved);
  return resolved;
}

export function setThemePreference(preference: ThemePreference): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // The choice still applies for this page load, it just won't persist.
  }
  applyTheme(preference);
  listeners.forEach((listener) => listener(preference));
}

export function subscribeToThemePreference(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Applies the stored preference and keeps a 'system' preference in sync with the OS setting.
 * Returns a function that removes the OS listener.
 */
export function initializeTheme(): () => void {
  applyTheme(getThemePreference());

  const query = window.matchMedia?.(DARK_QUERY);
  if (!query) {
    return () => {};
  }
  const onSystemChange = () => {
    if (getThemePreference() === 'system') {
      applyTheme('system');
    }
  };
  query.addEventListener('change', onSystemChange);
  return () => query.removeEventListener('change', onSystemChange);
}
