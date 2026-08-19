import { useEffect, useState } from 'react';
import type { Dispatch } from 'redux';

import * as Creators from '../actions/creators';
import { CanarySettings } from '../canary.settings';
import { MetricConfigMode } from '../domain/IMetricConfigMode';

/**
 * Derives the mode a metric's query editor should open in, based on whatever data is already
 * present: template data wins if present (it's the default going forward), otherwise guided data
 * (for existing configs), otherwise Template (the default for brand-new, still-empty metrics).
 */
export function deriveMetricConfigMode(hasTemplateData: boolean, hasGuidedData: boolean): MetricConfigMode {
  if (hasTemplateData) {
    return MetricConfigMode.TEMPLATE;
  }
  if (hasGuidedData) {
    return MetricConfigMode.GUIDED;
  }
  return MetricConfigMode.TEMPLATE;
}

/**
 * Tracks which of the Guided/Template forms should be shown for the metric currently being
 * edited.
 *
 * The initial value is derived from whatever data is already present on the metric, so existing
 * configs open showing whichever form matches their data. From then on the mode lives in local
 * component state: pure derivation can't distinguish "brand-new metric, no data yet, but the user
 * explicitly clicked Guided" from "brand-new metric, still defaulting to Template" once both
 * guided and template fields are empty, so a click needs somewhere to record intent that isn't
 * itself data. The mode re-derives from data whenever a different metric (by id) is loaded.
 *
 * When `CanarySettings.templatesEnabled` (the ops kill-switch) is off, the mode is pinned to
 * Guided and cannot be changed.
 */
export function useMetricConfigMode(
  metricId: string,
  hasTemplateData: boolean,
  hasGuidedData: boolean,
): [MetricConfigMode, (mode: MetricConfigMode) => void] {
  const templatesEnabled = CanarySettings.templatesEnabled !== false;

  const [mode, setMode] = useState<MetricConfigMode>(() =>
    templatesEnabled ? deriveMetricConfigMode(hasTemplateData, hasGuidedData) : MetricConfigMode.GUIDED,
  );

  useEffect(() => {
    setMode(templatesEnabled ? deriveMetricConfigMode(hasTemplateData, hasGuidedData) : MetricConfigMode.GUIDED);
    // Only re-derive when switching to a different metric -- not on every keystroke, which would
    // fight the user's explicit mode selection.
  }, [metricId]);

  const setModeGuarded = (next: MetricConfigMode) => setMode(templatesEnabled ? next : MetricConfigMode.GUIDED);

  return [mode, setModeGuarded];
}

/**
 * Clears all template-related state for the metric being edited. Dispatched whenever the mode
 * toggle changes (in either direction), mirroring Prometheus's pre-existing `queryType` toggle
 * behavior: guided fields are left untouched on switch (backend query builders already prefer a
 * populated template over guided fields, and preserving guided input means nothing is lost if the
 * user switches back), but stale template data is always cleared so it can't shadow fresh guided
 * input after switching to Guided.
 */
export function clearTemplateState(dispatch: Dispatch<any>): void {
  dispatch(Creators.editTemplateCancel());
  dispatch(Creators.selectTemplate({ name: null }));
  dispatch(Creators.editInlineTemplate({ value: '' }));
}
