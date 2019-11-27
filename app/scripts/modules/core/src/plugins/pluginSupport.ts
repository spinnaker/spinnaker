import * as lodash from 'lodash';
import * as react from 'react';
import * as react_dom from 'react-dom';
import * as spinnaker_core from '@spinnaker/core';

export const pluginSupport = {
  sharedLibraryNames: [
    'lodash',
    'react',
    'react-dom',
    // Temporarily expose spinnaker/core.
    // This should be removed at some point and replaced with a much smaller spinnaker/ui module which doesn't yet exist
    '@spinnaker/core',
  ],

  globalVariablePrefix: 'spinnaker.plugins.sharedLibraries',

  sanitizeLibraryName(libraryName: string): string {
    return libraryName.replace(/[^\w]/g, '_');
  },

  // Expose a subset of shared libraries on the global object, to be used by plugins
  exposeSharedLibraries(): void {
    const destinationObject = lodash.get(window, this.globalVariablePrefix) as any;

    const exposeSharedLibrary = (libraryName: string, library: any) => {
      const sanitizedLibraryName = this.sanitizeLibraryName(libraryName);
      destinationObject[sanitizedLibraryName] = library;
    };

    if (destinationObject) {
      exposeSharedLibrary('lodash', lodash);
      exposeSharedLibrary('react', react);
      exposeSharedLibrary('react-dom', react_dom);
      exposeSharedLibrary('@spinnaker/core', spinnaker_core);
    }
  },
};
