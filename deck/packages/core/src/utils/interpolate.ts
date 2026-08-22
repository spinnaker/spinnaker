export type Interpolator = (context: Record<string, any>) => string;

export function interpolate(template: string): Interpolator {
  return (context) =>
    template.replace(/{{\s*([^}]+?)\s*}}/g, (_match, expression: string) => {
      const value = expression
        .split('.')
        .map((part) => part.trim())
        .reduce((current, part) => current?.[part], context);

      return value === undefined || value === null ? '' : String(value);
    });
}
