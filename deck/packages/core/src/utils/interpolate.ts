export type Interpolator = (context: Record<string, any>) => string;

const applyFilters = (value: string, filters: string[]): string =>
  filters.reduce((current, filter) => {
    const replaceMatch = filter.match(/^replace:"([^"]*)":"([^"]*)"$/);
    if (replaceMatch) {
      const [, from, to] = replaceMatch;
      return current.split(from).join(to);
    }
    return current;
  }, value);

export function interpolate(template: string): Interpolator;
export function interpolate(template: string, context: Record<string, any>): string;
export function interpolate(template: string, context?: Record<string, any>): Interpolator | string {
  const interpolator: Interpolator = (ctx) =>
    template.replace(/{{\s*([^}]+?)\s*}}/g, (_match, expression: string) => {
      const [path, ...filters] = expression.split('|').map((part) => part.trim());
      const value = path
        .split('.')
        .map((part) => part.trim())
        .reduce((current, part) => current?.[part], ctx);

      if (value === undefined || value === null) {
        return '';
      }

      return applyFilters(String(value), filters);
    });

  return context === undefined ? interpolator : interpolator(context);
}
