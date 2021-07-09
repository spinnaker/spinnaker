import * as uiRouterCore from '@uirouter/core';
import * as uiRouterReact from '@uirouter/react';
import * as uiRouterRx from '@uirouter/rx';
import * as lodash from 'lodash';
import * as propTypes from 'prop-types';
import * as react from 'react';
import * as reactDOM from 'react-dom';
import * as rxjs from 'rxjs';

import * as spinnakerCore from '../index';

export const sharedLibraries = {
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

    // Updates here should also be added in packages/pluginsdk/pluginconfig/rollup.config.js
    if (destinationObject) {
      // Temporarily expose @spinnaker/core.
      // This should be removed at some point and replaced with a much smaller spinnaker/ui module which doesn't yet exist
      exposeSharedLibrary('@spinnaker/core', spinnakerCore);
      exposeSharedLibrary('@uirouter/core', uiRouterCore);
      exposeSharedLibrary('@uirouter/react', uiRouterReact);
      exposeSharedLibrary('@uirouter/rx', uiRouterRx);
      exposeSharedLibrary('lodash', lodash);
      exposeSharedLibrary('prop-types', propTypes);
      exposeSharedLibrary('react', react);
      exposeSharedLibrary('react-dom', reactDOM);
      exposeSharedLibrary('rxjs', rxjs);
      exposeSharedLibrary('rxjs/Observable', { Observable: rxjs.Observable });
    }
  },
};
