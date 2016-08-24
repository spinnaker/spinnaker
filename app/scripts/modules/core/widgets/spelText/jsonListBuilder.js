'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widget.jsonListBuilder', [])
  .factory('jsonListBuilder', function() {

    let convertJsonKeysToBracketedList = (json, ignoreList = []) => {
      let stack = [];
      let array = [];
      let parentList = [];

      stack.push(json);

      while(stack.length !== 0) {
        let node = stack.pop();
        let keys = Object.keys(node);
        let p = parentList.pop() || '';
        processKeys(keys, node, parentList, p, stack, array, ignoreList);
      }

      return array;
    };


    let processKeys = (keys, node, parentList, parent, stack, array, ignoreList) => {
      keys.forEach( (key) => {

        let entry = isFinite(parseInt(key)) ? `${parent}[${parseInt(key)}]` : `${parent}['${key}']`;

        let value = node[key];
        if( !(angular.isObject(value) || angular.isArray(value) ) ) {


          if ( !ignoreList.some( (ignoreItem) => {
              let testerString = `[\'${ignoreItem}`;
              return entry.substr(0, testerString.length) === testerString;
            })) {
            array.push({leaf: entry, value: value});

          }
        }

        if(angular.isObject(node[key])) {
          parentList.push(entry);
          stack.push(node[key]);
        }
      });
    };


    let escapeForRegEx = (item) => {
      if (item) {
        return item.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, '\\$&');
      }
    };


    return {
      convertJsonKeysToBracketedList: convertJsonKeysToBracketedList,
      escapeForRegEx: escapeForRegEx
    };
  });
