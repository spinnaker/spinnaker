'use strict';

import { module } from 'angular';

import { REST } from '@spinnaker/core';

export const ORACLE_IMAGE_IMAGE_READER = 'spinnaker.oracle.image.reader';
export const name = ORACLE_IMAGE_IMAGE_READER; // for backwards compatibility
module(ORACLE_IMAGE_IMAGE_READER, []).factory('oracleImageReader', function () {
  function findImages(params) {
    return REST('/images/find')
      .query(params)
      .get()
      .catch(function () {
        return [];
      });
  }

  function getImage(imageId, region, credentials) {
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

  return {
    findImages,
    getImage,
  };
});
