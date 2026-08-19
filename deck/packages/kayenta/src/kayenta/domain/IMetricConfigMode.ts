/**
 * Which form is shown for configuring a metric's query: the raw, always-available `Template`
 * editor (bound to `query.customInlineTemplate`/`query.customFilterTemplate`), or a provider's
 * structured `Guided` form (soft-deprecated, kept fully functional for existing configs).
 */
export enum MetricConfigMode {
  GUIDED = 'GUIDED',
  TEMPLATE = 'TEMPLATE',
}
