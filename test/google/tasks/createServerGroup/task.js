'use strict';

let job = require('./job.js'),
  getImages = require('../utils/getImages'),
  { account, cloudProvider, region, zone } = require('../../config.json');

function createServerGroupTask (appName) {
  return getImages()
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

module.exports = createServerGroupTask;
