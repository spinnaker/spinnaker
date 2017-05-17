'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.filterModel.dependentFilter.service', [])
  .factory('dependentFilterService', function () {

    function digestDependentFilters ({ pool, dependencyOrder, sortFilter }) {
      return dependencyOrder.reduce(generateIterator(sortFilter), {pool, headings: {}}).headings;
    }

    function generateIterator (sortFilter) {
      return function iterator (acc, headingType) {
        let { headings, pool } = acc;
        headings[headingType] = grabHeadingsForHeadingType(pool, headingType);
        unselectUnavailableHeadings(headings[headingType], sortFilter[headingType]);
        acc.pool = filterPoolBySelectedHeadings(pool, headingType, sortFilter);
        return acc;
      };
    }

    function grabHeadingsForHeadingType (pool, headingType) {
      return _.chain(pool).map(headingType).uniq().compact().value();
    }

    function filterPoolBySelectedHeadings (pool, headingType, sortFilter) {
      let selectedHeadings = sortFilter[headingType];
      if (!mapTruthyHashKeysToList(selectedHeadings).length) {
        return pool;
      }

      return pool.filter((unit) => selectedHeadings[unit[headingType]]);
    }

    function unselectUnavailableHeadings (headings, selectedHeadings) {
      if (!selectedHeadings) {
        return;
      }

      let headingSet = setBuilder(headings);
      Object.keys(selectedHeadings).forEach((key) => {
        if (!headingSet[key]) {
          delete selectedHeadings[key];
        }
      });
    }

    function setBuilder (array) {
      return array.reduce((s, el) => {
        if (!(el in s)) {
          s[el] = true;
        }
        return s;
      }, {});
    }

    function mapTruthyHashKeysToList (hash) {
      return Object.keys(_.pickBy(hash, _.identity));
    }

    return { digestDependentFilters };
  });
