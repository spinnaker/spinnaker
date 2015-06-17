/*
 * angular-marked
 * (c) 2014 J. Harshbarger
 * Licensed MIT
 */

/* jshint undef: true, unused: true */
/* global angular:true */
/* global marked:true */

(function () {
	'use strict';

  /**
   * @ngdoc overview
   * @name index
   *
   * @description
   * AngularJS Markdown using [marked](https://github.com/chjj/marked).
   *
   * ## Why?
   *
   * I wanted to use [marked](https://github.com/chjj/marked) instead of [showdown](https://github.com/coreyti/showdown) as used in [angular-markdown-directive](https://github.com/btford/angular-markdown-directive) as well as expose the option to globally set defaults.
   *
   * ## How?
   *
   * - {@link hc.marked.directive:marked As a directive}
   * - {@link hc.marked.service:marked As a service}
   * - {@link hc.marked.service:markedProvider Set default options}
   *
   * @example

      Convert markdown to html at run time.  For example:

      <example module="hc.marked">
        <file name=".html">
          <form ng-controller="MainController">
            Markdown:<br />
            <textarea ng-model="my_markdown" cols="60" rows="5" class="span8" /><br />
            Output:<br />
            <div marked="my_markdown" />
          </form>
        </file>
        <file  name=".js">
          function MainController($scope) {
            $scope.my_markdown = "*This* **is** [markdown](https://daringfireball.net/projects/markdown/)";
          }
        </file>
      </example>

    *
    */

    /**
     * @ngdoc overview
     * @name hc.marked
     * @description # angular-marked (core module)
       # Installation
      First include angular-marked.js in your HTML:

      ```js
        <script src="angular-marked.js">
      ```

      Then load the module in your application by adding it as a dependency:

      ```js
      angular.module('yourApp', ['hc.marked']);
      ```

      With that you're ready to get started!
     */

  angular.module('hc.marked', [])

    /**
    * @ngdoc service
    * @name hc.marked.service:marked
		* @requires $window
    * @description
    * A reference to the [marked](https://github.com/chjj/marked) parser.
    *
    * @example
    <example module="hc.marked">
      <file name=".html">
        <div ng-controller="MainController">
          html: {{html}}
        </div>
      </file>
      <file  name=".js">
        function MainController($scope, marked) {
          $scope.html = marked('#TEST');
        }
      </file>
    </example>
   **/

   /**
   * @ngdoc service
   * @name hc.marked.service:markedProvider
   * @description
   * Use `markedProvider` to change the default behavior of the {@link hc.marked.service:marked marked} service.
   *
	 * @example

		## Example using [google-code-prettify syntax highlighter](https://code.google.com/p/google-code-prettify/) (must include google-code-prettify.js script).  Also works with [highlight.js Javascript syntax highlighter](http://highlightjs.org/).

		<example module="myApp">
		<file name=".js">
		angular.module('myApp', ['hc.marked'])
			.config(['markedProvider', function(markedProvider) {
				markedProvider.setOptions({
					gfm: true,
					tables: true,
					highlight: function (code) {
						return prettyPrintOne(code);
					}
				});
			}]);
		</file>
		<file name=".html">
			<marked>
			```js
			angular.module('myApp', ['hc.marked'])
				.config(['markedProvider', function(markedProvider) {
					markedProvider.setOptions({
						gfm: true,
						tables: true,
						highlight: function (code) {
							return prettyPrintOne(code);
						}
					});
				}]);
			```
			</marked>
		</file>
		</example>
  **/

  .provider('marked', function () {

    var self = this;

    /**
     * @ngdoc method
     * @name markedProvider#setOptions
     * @methodOf hc.marked.service:markedProvider
     *
     * @param {object} opts Default options for [marked](https://github.com/chjj/marked#options-1).
     */

    self.setOptions = function(opts) {  // Store options for later
      this.defaults = opts;
    };

    self.$get = ['$window', function ($window) {
      var m = $window.marked || marked;

      self.setOptions = m.setOptions;
      m.setOptions(self.defaults);

      return m;
    }];

  })

  // TODO: filter tests */
  //app.filter('marked', ['marked', function(marked) {
	//  return marked;
	//}]);

  /**
   * @ngdoc directive
   * @name hc.marked.directive:marked
   * @restrict AE
   * @element any
   *
   * @description
   * Compiles source test into HTML.
   *
   * @param {expression} marked The source text to be compiled.  If blank uses content as the source.
   * @param {expression=} opts Hash of options that override defaults.
   *
   * @example

     ## A simple block of text

      <example module="hc.marked">
        <file name="exampleA.html">
         * <marked>
         * ### Markdown directive
         *
         * *It works!*
         *
         * *This* **is** [markdown](https://daringfireball.net/projects/markdown/) in the view.
         * </marked>
        </file>
      </example>

     ## Bind to a scope variable

      <example module="hc.marked">
        <file name="exampleB.html">
          <form ng-controller="MainController">
            Markdown:<br />
            <textarea ng-model="my_markdown" class="span8" cols="60" rows="5"></textarea><br />
            Output:<br />
            <blockquote marked="my_markdown"></blockquote>
          </form>
        </file>
        <file  name="exampleB.js">
          * function MainController($scope) {
          *   $scope.my_markdown = '*This* **is** [markdown](https://daringfireball.net/projects/markdown/)';
					*   $scope.my_markdown += ' in a scope variable';
          * }
        </file>
      </example>

      ## Include a markdown file:

       <example module="hc.marked">
         <file name="exampleC.html">
           <div marked ng-include="'include.html'" />
         </file>
				 * <file name="include.html">
				 * *This* **is** [markdown](https://daringfireball.net/projects/markdown/) in a include file.
				 * </file>
       </example>
   */

  .directive('marked', ['marked', function (marked) {
    return {
      restrict: 'AE',
      replace: true,
      scope: {
        opts: '=',
        marked: '='
      },
      link: function (scope, element, attrs) {
        set(scope.marked || element.text() || '');

        function set(val) {
          element.html(marked(val || '', scope.opts || null));
        }

        if (attrs.marked) {
          scope.$watch('marked', set);
        }

      }
    };
  }]);

}());
