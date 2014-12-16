'use strict';


angular.module('deckApp')
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
