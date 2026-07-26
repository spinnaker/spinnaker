import { REST } from '@spinnaker/core';

export class AzureImageReader {
  findImages(params) {
    return REST('/images/find')
      .query(params)
      .get()
      .then(
        function (results) {
          return results;
        },
        function () {
          return [];
        },
      );
  }

  getImage(amiName, region, credentials) {
    return REST('/images')
      .path(credentials, region, amiName)
      .query({ provider: 'azure' })
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
