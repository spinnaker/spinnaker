'use strict';

let request = require('request-promise'),
  { cloudProvider, gateUrl } = require('../../config.json'),
  uri = `${gateUrl}/images/find?provider=${cloudProvider}&q=`;

function findImages () {
  let config = {
    method: 'GET',
    json: true,
    uri: uri
  };

  return request(config)
    .then(imageDescriptions => imageDescriptions.map(r => r.imageName));
}

module.exports = findImages;
