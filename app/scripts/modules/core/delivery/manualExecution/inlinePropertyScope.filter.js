'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.manualExecution.inlineProperty.filter', [])
  .filter('inlinePropertyScope', function() {
    return function(json) {
      const keyTransforms = {
        'env': 'Env',
        'appIdList': 'Apps',
        'cluster': 'Cluster',
        'asg': 'ASG',
        'region': 'Region',
        'stack': 'Stack'
      };



      return Object.keys(json).reduce((acc, key) => {
        let value = json[key];
        if (value.length) {

          let newKey = keyTransforms[key] || key;

          if(Array.isArray(value)) {
            let values = value.join();
            if(values.length) {
              acc.push(`${newKey}: ${values}`);
            }
          } else {
            acc.push(`${newKey}: ${value}`);
          }
        }
        return acc;
      },[]).join(', ');
    };
  });
