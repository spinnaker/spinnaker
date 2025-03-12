declare module '*.svg' {
  import type { ComponentType, SVGProps } from 'react';
  export type SVGComponent = ComponentType<SVGProps<HTMLOrSVGElement>>;
  export const ReactComponent: SVGComponent;
  const URL: string;
  export default URL;
}
