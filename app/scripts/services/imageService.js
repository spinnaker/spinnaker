'use strict';


angular.module('spinnaker.image.service', [
  'spinnaker.aws.image.service',
  'spinnaker.gce.image.service'
])
  .factory('imageService', function (awsImageService, gceImageService) {

    function getDelegate(provider) {
      return (!provider || provider === 'aws') ? awsImageService : gceImageService;
    }

    function findImages(params) {
      return getDelegate(params.provider).findImages(params);
    }

    function getAmi(selectedProvider, amiName, region, credentials) {
      return getDelegate(selectedProvider).getAmi(amiName, region, credentials);
    }

    return {
      findImages: findImages,
      getAmi: getAmi,
    };
  });
