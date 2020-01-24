import * as lodash from 'lodash';
import * as react from 'react';
import * as reactDOM from 'react-dom';
import * as spinnakerCore from 'core';

export const sharedLibraries = {
  sharedLibraryNames: [
    'lodash',
    'react',
    'react-dom',
    // Temporarily expose spinnaker/core.
    // This should be removed at some point and replaced with a much smaller spinnaker/ui module which doesn't yet exist
    '@spinnaker/core',
  ],

  // This is the global (window) variable that the shared libs will be exposed on
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
      exposeSharedLibrary('react-dom', reactDOM);
      exposeSharedLibrary('@spinnaker/core', spinnakerCore);
    }
  },
};
