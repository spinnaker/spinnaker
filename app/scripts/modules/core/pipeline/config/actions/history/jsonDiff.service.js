'use strict';

/* eslint no-underscore-dangle: 0 */

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.utils.diff.service', [])
  .factory('jsonDiffService', function () {

    let DiffMatchPatch = require('expose?diff_match_patch!diff-match-patch');

    function generateDiff(left, right) {
      let dmp = new DiffMatchPatch();
      let a = dmp.diff_linesToChars_(left, right);
      let diffs = dmp.diff_main(a.chars1, a.chars2, false);
      dmp.diff_charsToLines_(diffs, a.lineArray);
      return diffs;
    }

    function diff(left, right) {
      let diffs = generateDiff(left, right);
      let diffLines = [];
      let additions = 0, removals = 0, unchanged = 0, total = 0;
      let changeBlocks = [];
      diffs.forEach(diff => {
        let lines = diff[1].split('\n');
        if (lines[lines.length - 1] === '') {
          lines.pop(); // always a trailing new line...
        }
        let type = 'match';
        if (diff[0] === 1) {
          type = 'add';
          additions += lines.length;
          changeBlocks.push({type: type, lines: lines.length, start: total});
        }
        if (diff[0] === -1) {
          type = 'remove';
          removals += lines.length;
          changeBlocks.push({type: type, lines: lines.length, start: total});
        }
        if (diff[0] === 0) {
          unchanged += lines.length;
        }
        lines.forEach(l => diffLines.push({type: type, text: l}));
        total += lines.length;
      });
      changeBlocks.forEach(b => {
        b.height = b.lines * 100 / total;
        b.top = b.start * 100 / total;
      });
      return {
        details: diffLines,
        summary: { additions, removals, unchanged, total },
        changeBlocks,
      };
    }

    return {
      diff,
    };
  });
