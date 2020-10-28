declare module '*.svg' {
  import { ComponentType, SVGProps } from 'react';
  export const ReactComponent: ComponentType<SVGProps<HTMLOrSVGElement>>;
  const URL: string;
  export default URL;
}
