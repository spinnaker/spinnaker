import { SETTINGS } from '../../config/settings';
import {
  THEME_ATTRIBUTE,
  THEME_STORAGE_KEY,
  applyTheme,
  getThemePreference,
  initializeTheme,
  resolveTheme,
  setThemePreference,
  subscribeToThemePreference,
} from './theme';

function mockSystemDark(matches: boolean) {
  const listeners = new Set<() => void>();
  const query = {
    matches,
    addEventListener: (_: string, l: () => void) => listeners.add(l),
    removeEventListener: (_: string, l: () => void) => listeners.delete(l),
  };
  window.matchMedia = vi.fn().mockReturnValue(query) as any;
  return {
    flip: (next: boolean) => {
      query.matches = next;
      listeners.forEach((l) => l());
    },
    listeners,
  };
}

describe('theme', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute(THEME_ATTRIBUTE);
    mockSystemDark(false);
    delete SETTINGS.defaultTheme;
  });

  it('defaults to light and ignores unknown stored values', () => {
    expect(getThemePreference()).toBe('light');
    window.localStorage.setItem(THEME_STORAGE_KEY, 'solarized');
    expect(getThemePreference()).toBe('light');
  });

  it('falls back to settings.defaultTheme, and a stored choice wins over it', () => {
    SETTINGS.defaultTheme = 'dark';
    expect(getThemePreference()).toBe('dark');

    SETTINGS.defaultTheme = 'bogus' as any;
    expect(getThemePreference()).toBe('light');

    SETTINGS.defaultTheme = 'dark';
    setThemePreference('light');
    expect(getThemePreference()).toBe('light');
  });

  it('resolves "system" from prefers-color-scheme', () => {
    mockSystemDark(true);
    expect(resolveTheme('system')).toBe('dark');
    mockSystemDark(false);
    expect(resolveTheme('system')).toBe('light');
    expect(resolveTheme('dark')).toBe('dark');
  });

  it('persists the preference, applies it to <html>, and notifies subscribers', () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToThemePreference(listener);

    setThemePreference('dark');

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('dark');
    expect(listener).toHaveBeenCalledWith('dark');

    unsubscribe();
    setThemePreference('light');
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('still applies the theme when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    expect(() => setThemePreference('dark')).not.toThrow();
    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('dark');
    vi.restoreAllMocks();
  });

  it('follows OS changes only while the preference is "system"', () => {
    const system = mockSystemDark(false);
    window.localStorage.setItem(THEME_STORAGE_KEY, 'system');
    const dispose = initializeTheme();
    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('light');

    system.flip(true);
    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('dark');

    window.localStorage.setItem(THEME_STORAGE_KEY, 'light');
    applyTheme('light');
    system.flip(true);
    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('light');

    dispose();
    expect(system.listeners.size).toBe(0);
  });
});
