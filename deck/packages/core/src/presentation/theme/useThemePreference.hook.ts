import { useEffect, useState } from 'react';

import type { ThemePreference } from './theme';
import { getThemePreference, setThemePreference, subscribeToThemePreference } from './theme';

export function useThemePreference(): [ThemePreference, (preference: ThemePreference) => void] {
  const [preference, setPreference] = useState<ThemePreference>(getThemePreference);
  useEffect(() => subscribeToThemePreference(setPreference), []);
  return [preference, setThemePreference];
}
