import { forIn, isPlainObject } from 'lodash';

/**
 * Deeply walks an object tree and invokes `callback` for each node
 *
 * @param object the object to walk
 * @param callback the callback to invoke on each simple leaf nodes.
 *        This callback receives the `path` and `value`.
 *        The `path` is a string representing the path into the object, which is compatible with lodash _.get(key).
 *        The `value` is the value of the simple leaf node.
 * @param traverseLeafNodesOnly When true, only walks simple leaf nodes,
 *        i.e., properties that are neither a nested object, nor an array.
 */
export const traverseObject = (object: object, callback: ITraverseCallback, traverseLeafNodesOnly = false) => {
  return _traverseObject(null, object, callback, traverseLeafNodesOnly);
};

type ITraverseCallback = (path: string, obj: object) => void;

const _traverseObject = (
  contextPath: string,
  obj: object,
  callback: ITraverseCallback,
  traverseLeafNodesOnly: boolean,
) => {
  function maybeInvokeCallback(isLeafNode: boolean) {
    if (contextPath !== null && (isLeafNode || !traverseLeafNodesOnly)) {
      callback(contextPath, obj);
    }
  }

  if (isPlainObject(obj)) {
    maybeInvokeCallback(false);
    forIn(obj, (val, key) => {
      const path = contextPath ? contextPath + '.' : '';
      return _traverseObject(`${path}${key}`, val, callback, traverseLeafNodesOnly);
    });
  } else if (Array.isArray(obj)) {
    maybeInvokeCallback(false);
    obj.forEach((val, idx) => _traverseObject(`${contextPath}[${idx}]`, val, callback, traverseLeafNodesOnly));
  } else {
    maybeInvokeCallback(true);
  }
};
