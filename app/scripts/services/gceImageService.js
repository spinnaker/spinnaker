'use strict';


angular.module('deckApp')
  .factory('gceImageService', function ($q) {

    // TODO(duftler): Call oort once oort can return GCE images.
    function findImages(query, region, account) {
      return $q.when(['debian-7-wheezy-v20141108', 'centos-7-v20141108']);
    }

    // TODO(duftler): Call oort once oort can return GCE images.
    // TODO(duftler): Rename getAmi() to getImage()?
    function getAmi(amiName, region, credentials) {
      return null;
    }

    return {
      findImages: findImages,
      getAmi: getAmi,
    };
  });
