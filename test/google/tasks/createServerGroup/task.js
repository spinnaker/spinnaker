'use strict';

let job = require('./job.js'),
  findImages = require('../utils/findImages'),
  { account, cloudProvider, region, zone } = require('../../config.json');

function createServerGroupJob (appName) {
  return findImages()
    .then((images) => {
      let image = images[0],
        task = {
          application: appName,
          description: `Create New Server Group in cluster ${appName}`,
          job: [job({ appName, image, account, cloudProvider, region, zone })]
        };

      return task;
    });
}

module.exports = createServerGroupJob;
