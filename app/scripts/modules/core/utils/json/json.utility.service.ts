import {module} from 'angular';
import {isPlainObject, isArray, isNumber, isString} from 'lodash';

const DiffMatchPatch = require('expose?diff_match_patch!diff-match-patch');

interface IDiffDetails {
  type: string;
  text: string;
}

interface IChangeBlock {
  start: number;
  height: number;
  top: number;
  type: string;
  lines: number;
}

interface IDiffSummary {
  additions: number;
  removals: number;
  unchanged: number;
  total: number;
}

export interface IJsonDiff {
  details: IDiffDetails[];
  changeBlocks: IChangeBlock[];
  summary: IDiffSummary;
}

export class JsonUtilityService {

  private generateDiff(left: string, right: string): [[number, string]] {
    let dmp: any = new DiffMatchPatch();
    let a = dmp.diff_linesToChars_(left, right);
    let diffs = dmp.diff_main(a.chars1, a.chars2, false);
    dmp.diff_charsToLines_(diffs, a.lineArray);
    return diffs;
  }

  private sortObject(o: any): any {
    if (!o || isNumber(o) || isString(o)) {
      return o;
    }
    // sorting based on http://stackoverflow.com/questions/5467129/sort-javascript-object-by-key/29622653#29622653
    return Object.keys(o).sort().reduce((r: any, k: string) => {
      if (isPlainObject(o[k])) {
        r[k] = this.sortObject(o[k]);
      } else if (isArray(o[k])) {
        r[k] = o[k].map((o2: any) => this.sortObject(o2));
      } else {
        r[k] = o[k];
      }
      return r;
    }, {});
  }

  private makeSortedString(str: string): string {
    return this.makeSortedStringFromObject(JSON.parse(str));
  }

  public makeSortedStringFromObject(obj: any): string {
    return JSON.stringify(this.sortObject(obj), null, 2);
  }

  public diff(left: any, right: any, sortKeys = false): IJsonDiff {
    if (sortKeys) {
      left = this.makeSortedString(left);
      right = this.makeSortedString(right);
    }
    let diffs = this.generateDiff(left, right);
    let diffLines: IDiffDetails[] = [];
    let additions = 0, removals = 0, unchanged = 0, total = 0;
    let changeBlocks: IChangeBlock[] = [];
    diffs.forEach(diff => {
      let lines = diff[1].split('\n');
      if (lines[lines.length - 1] === '') {
        lines.pop(); // always a trailing new line...
      }
      let type = 'match';
      if (diff[0] === 1) {
        type = 'add';
        additions += lines.length;
        changeBlocks.push({type: type, lines: lines.length, start: total, height: null, top: null});
      }
      if (diff[0] === -1) {
        type = 'remove';
        removals += lines.length;
        changeBlocks.push({type: type, lines: lines.length, start: total, height: null, top: null});
      }
      if (diff[0] === 0) {
        unchanged += lines.length;
      }
      lines.forEach((l: string) => diffLines.push({type: type, text: l}));
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
}

export const JSON_UTILITY_SERVICE = 'spinnaker.core.utils.json.service';
module(JSON_UTILITY_SERVICE, []).service('jsonUtilityService', JsonUtilityService);
