declare module '*.svg' {
  import type { ComponentType, SVGProps } from 'react';
  export const ReactComponent: ComponentType<SVGProps<HTMLOrSVGElement>>;
  const URL: string;
  export default URL;
}
