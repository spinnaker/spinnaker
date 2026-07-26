import type * as React from 'react';

type ProjectDashboardElementProps = React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement>;

declare global {
  namespace JSX {
    interface IntrinsicElements {
      'project-cluster': ProjectDashboardElementProps;
      'project-pipeline': ProjectDashboardElementProps;
      'region-filter': ProjectDashboardElementProps;
    }
  }
}

export {};
