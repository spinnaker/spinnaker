'use strict';

import { REST } from '@spinnaker/core';

export class OracleImageReader {
  findImages(params) {
    return REST('/images/find')
      .query(params)
      .get()
      .catch(function () {
        return [];
      });
  }

  getImage(imageId, region, credentials) {
    return REST('/images')
      .path(credentials, region, imageId)
      .query({ provider: 'oracle' })
      .get()
      .then(
        function (results) {
          return results && results.length ? results[0] : null;
        },
        function () {
          return null;
        },
      );
  }
}
