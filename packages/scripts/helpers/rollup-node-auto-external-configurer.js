const path = require('path');
/**
 * `rollup-plugin-auto-external` plugin doesn't handle import statements with paths in them. For example
 * `bootstrap/dist/js/bootstrap` will not be treated as an external. This function is a very simple externals configurer
 * that accepts a map of externals (packages and others) and correctly marks them as externals.
 *
 * The caller can typically pass `packageJSON.dependencies` as `externals`.
 */
module.exports = (externals) => {
  return (id) => {
    if (id.startsWith('.') || id.startsWith('/')) {
      return false;
    }
    if (id in externals) {
      return true;
    }

    // Handling imports with paths in them. We split the id by `/` and use either the first part or the first + second
    // part (in case of scoped packages) to verify if it is available in `externals`.
    const packageParts = id.split('/');
    if (packageParts.length > 1) {
      // If it is a scoped package, then include the second part of the package name.
      const packageIdentifier = id.startsWith('@') ? packageParts[0] + path.sep + packageParts[1] : packageParts[0];

      return packageIdentifier in externals;
    }

    return false;
  };
};
