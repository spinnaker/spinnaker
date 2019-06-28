import * as React from 'react';

/**
 * A helper for rendering "render prop" contents
 *
 * Supports:
 *
 * - React Class
 * - Arrow Function
 * - Render Function
 * - JSX.Element or string
 */
export function renderContent<T>(Content: string | JSX.Element | React.ComponentType<T>, props: T): React.ReactNode {
  const prototype = typeof Content === 'function' && Content.prototype;

  if (prototype && (prototype.isReactComponent || typeof prototype.render === 'function')) {
    const ClassComponent = Content as React.ComponentType<T>;
    return <ClassComponent {...props} />;
  } else if (typeof Content === 'function') {
    const arrowOrSFC = Content as (props: T) => JSX.Element;
    return arrowOrSFC(props);
  } else {
    return Content;
  }
}
