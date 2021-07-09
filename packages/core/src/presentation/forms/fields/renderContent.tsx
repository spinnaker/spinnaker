import { isFunction } from 'lodash';
import React from 'react';

/**
 * A helper for rendering "render prop" contents
 *
 * Supports:
 * - Render Function
 * - ReactNode (JSX.Element or string)
 */
export function renderContent<T>(Content: React.ReactNode | React.FunctionComponent<T>, props: T): React.ReactNode {
  if (isFunction(Content)) {
    const renderFunction = Content;
    return renderFunction(props);
  }

  return Content;
}
