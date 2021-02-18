import { SpelExpression, SpelExpressionEvaluator } from 'spel2js';

const literalExpression = (literalString: string) =>
  SpelExpressionEvaluator.compile(`'${literalString.replace(/'/g, "''")}'`);

/**
 * This captures a type of bracket and the position in which it occurs in the
 * expression. The positional information is used if an error has to be reported
 * because the related end bracket cannot be found. Bracket is used to describe:
 * square brackets [] round brackets () and curly brackets {}
 */
class Bracket {
  public static theOpenBracketFor(closeBracket: string): string {
    if (closeBracket === '}') {
      return '{';
    } else if (closeBracket === ']') {
      return '[';
    }
    return '(';
  }

  public static theCloseBracketFor(openBracket: string): string {
    if (openBracket === '{') {
      return '}';
    } else if (openBracket === '[') {
      return ']';
    }
    return ')';
  }

  constructor(public bracket: string, public pos: number) {}

  public compatibleWithCloseBracket(closeBracket: string): boolean {
    if (this.bracket === '{') {
      return closeBracket === '}';
    } else if (this.bracket === '[') {
      return closeBracket === ']';
    }
    return closeBracket === ')';
  }
}

/**
 * Ported from spring-expression-4.3.9 by Chris Thielen 2017
 */

/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An expression parser that understands templates. It can be subclassed by expression
 * parsers that do not offer first class support for templating.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Andy Clement
 * @since 3.0
 */
class TemplateAwareExpressionParser {
  /**
   * Helper that parses given expression string using the configured parser. The
   * expression string can contain any number of expressions all contained in "${...}"
   * markers. For instance: "foo${expr0}bar${expr1}". The static pieces of text will
   * also be returned as Expressions that just return that static piece of text. As a
   * result, evaluating all returned expressions and concatenating the results produces
   * the complete evaluated string. Unwrapping is only done of the outermost delimiters
   * found, so the string 'hello ${foo${abc}}' would break into the pieces 'hello ' and
   * 'foo${abc}'. This means that expression languages that used ${..} as part of their
   * functionality are supported without any problem. The parsing is aware of the
   * structure of an embedded expression. It assumes that parentheses '(', square
   * brackets '[' and curly brackets '}' must be in pairs within the expression unless
   * they are within a string literal and a string literal starts and terminates with a
   * single quote '.
   * @param expressionString the expression string
   * @return the parsed expressions
   * @throws ParseException when the expressions cannot be parsed
   */
  public parseExpressions(expressionString: string): SpelExpression[] {
    const expressions: SpelExpression[] = [];
    const prefix = '${';
    const suffix = '}';

    let startIdx = 0;
    while (startIdx < expressionString.length) {
      const prefixIndex = expressionString.indexOf(prefix, startIdx);
      if (prefixIndex >= startIdx) {
        // an inner expression was found - this is a composite
        if (prefixIndex > startIdx) {
          expressions.push(literalExpression(expressionString.substring(startIdx, prefixIndex)));
        }

        const afterPrefixIndex = prefixIndex + prefix.length;
        const suffixIndex = this.skipToCorrectEndSuffix(suffix, expressionString, afterPrefixIndex);
        if (suffixIndex === -1) {
          throw new Error(
            "No ending suffix '" +
              suffix +
              "' for expression starting at character " +
              prefixIndex +
              ': ' +
              expressionString.substring(prefixIndex),
          );
        }

        if (suffixIndex === afterPrefixIndex) {
          throw new Error(
            "No expression defined within delimiter '" + prefix + suffix + "' at character " + prefixIndex,
          );
        }

        const expr = expressionString.substring(prefixIndex + prefix.length, suffixIndex).trim();

        if (!expr) {
          throw new Error(
            "No expression defined within delimiter '" + prefix + suffix + "' at character " + prefixIndex,
          );
        }

        expressions.push(SpelExpressionEvaluator.compile(expr));
        startIdx = suffixIndex + suffix.length;
      } else {
        // no more ${expressions} found in string, add rest as static text
        expressions.push(literalExpression(expressionString.substring(startIdx)));
        startIdx = expressionString.length;
      }
    }

    return expressions;
  }

  /**
   * Return true if the specified suffix can be found at the supplied position in the
   * supplied expression string.
   * @param expressionString the expression string which may contain the suffix
   * @param pos the start position at which to check for the suffix
   * @param suffix the suffix string
   */
  private isSuffixHere(expressionString: string, pos: number, suffix: string): boolean {
    let suffixPosition = 0;
    for (let i = 0; i < suffix.length && pos < expressionString.length; i++) {
      if (expressionString.charAt(pos++) !== suffix.charAt(suffixPosition++)) {
        return false;
      }
    }
    if (suffixPosition !== suffix.length) {
      // the expressionString ran out before the suffix could entirely be found
      return false;
    }
    return true;
  }

  /**
   * Copes with nesting, for example '${...${...}}' where the correct end for the first
   * ${ is the final }.
   * @param suffix the suffix
   * @param expressionString the expression string
   * @param afterPrefixIndex the most recently found prefix location for which the
   * matching end suffix is being sought
   * @return the position of the correct matching nextSuffix or -1 if none can be found
   */
  private skipToCorrectEndSuffix(suffix: string, expressionString: string, afterPrefixIndex: number): number {
    // Chew on the expression text - relying on the rules:
    // brackets must be in pairs: () [] {}
    // string literals are "..." or '...' and these may contain unmatched brackets
    let pos: number = afterPrefixIndex;
    const maxlen: number = expressionString.length;
    const nextSuffix: number = expressionString.indexOf(suffix, afterPrefixIndex);
    if (nextSuffix === -1) {
      return -1; // the suffix is missing
    }
    const stack: Bracket[] = [];
    while (pos < maxlen) {
      if (this.isSuffixHere(expressionString, pos, suffix) && !stack.length) {
        break;
      }
      const ch: string = expressionString.charAt(pos);
      switch (ch) {
        case '{':
        case '[':
        case '(':
          stack.push(new Bracket(ch, pos));
          break;
        case '}':
        case ']':
        case ')': {
          if (!stack.length) {
            throw new Error(
              `Found closing '${ch}' at position ${pos} without an opening '${Bracket.theOpenBracketFor(ch)}'`,
            );
          }

          const p: Bracket = stack.pop();
          if (!p.compatibleWithCloseBracket(ch)) {
            throw new Error(
              "Found closing '" +
                ch +
                "' at position " +
                pos +
                " but most recent opening is '" +
                p.bracket +
                "' at position " +
                p.pos,
            );
          }
          break;
        }
        case "'":
        case '"': {
          // jump to the end of the literal
          const endLiteral = expressionString.indexOf(ch, pos + 1);
          if (endLiteral === -1) {
            throw new Error('Found non terminating string literal starting at position ' + pos);
          }
          pos = endLiteral;
          break;
        }
      }
      pos++;
    }
    if (stack.length) {
      const p: Bracket = stack.pop();
      throw new Error(
        "Missing closing '" + Bracket.theCloseBracketFor(p.bracket) + "' for '" + p.bracket + "' at position " + p.pos,
      );
    }
    if (!this.isSuffixHere(expressionString, pos, suffix)) {
      return -1;
    }
    return pos;
  }
}

export function parseSpelExpressions(template: string): SpelExpression[] {
  const spelExpressions = new TemplateAwareExpressionParser().parseExpressions(template);

  // A Monkey patch which adds the current context when an exception occurs
  spelExpressions.forEach((expr) => {
    const getValue = expr._compiledExpression.getValue;
    expr._compiledExpression.getValue = function () {
      const state = arguments[0];
      try {
        return getValue.apply(expr._compiledExpression, arguments);
      } catch (err) {
        err.state = state;
        throw err;
      }
    };
  });

  return spelExpressions;
}
