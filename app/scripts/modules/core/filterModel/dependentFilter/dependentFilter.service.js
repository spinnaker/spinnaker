'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.filterModel.dependentFilter.service', [
  require('../../utils/lodash.js')
])
  .factory('dependentFilterService', function (_) {

    function digestDependentFilters ({ pool, dependencyOrder, sortFilter }) {
      let updatedHeadings = dependencyOrder.reduce(generateIterator(sortFilter), { pool, headings: {} }).headings;
      return updatedHeadings;
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
      return _(pool).pluck(headingType).uniq().compact().valueOf();
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
      return Object.keys(_.pick(hash, _.identity));
    }

    return { digestDependentFilters };
  });
