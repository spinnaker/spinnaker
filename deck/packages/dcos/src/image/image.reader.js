import { REST } from '@spinnaker/core';

function findImages(params) {
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

function getImage(/*amiName, region, account*/) {
  // dcos images are not regional so we don't need to retrieve ids scoped to regions.
  return null;
}

export const dcosImageReader = {
  findImages,
  getImage,
};
