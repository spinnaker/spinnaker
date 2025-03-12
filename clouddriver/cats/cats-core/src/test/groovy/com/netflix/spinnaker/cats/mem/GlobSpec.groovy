/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.mem

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GlobSpec extends Specification {
    @Subject
    InMemoryCache.Glob glob

    @Unroll("fixed string: #globString #matches #candidate")
    def 'no special characters is exact match only'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString | candidate | expected
        'foo'      | 'foo'     | true
        'foo'      | 'foot'    | false
        'foo'      | 'afoo'    | false
        'foo'      | 'foofoo'  | false
        matches = expected ? 'matches' : 'does not match'
    }

    @Unroll("wildcard char: #globString #matches #candidate")
    def 'wildcard character tests'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString | candidate | expected
        'f?o'      | 'foo'     | true
        'f?o'      | 'foot'    | false
        'f??'      | 'fii'     | true
        'f??'      | 'fio'     | true
        '?f?'      | 'ofo'     | true
        '???'      | 'fi'      | false
        matches = expected ? 'matches' : 'does not match'
    }

    @Unroll("wildcard string: #globString #matches #candidate")
    def 'wildcard string tests'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString | candidate   | expected
        'fo*'      | 'fo'        | true
        'fo*'      | 'foo'       | true
        'fo*'      | 'fooooo'    | true
        '*oof'     | 'foof'      | true
        '*oof'     | 'whoof'     | true
        'f*f'      | 'ff'        | true
        'f*f'      | 'fof'       | true
        'f*f'      | 'fff'       | true
        'f*f'      | 'fofofofof' | true
        'f*f'      | 'fooofa'    | false
        'f*f*'     | 'fifa'      | true
        matches = expected ? 'matches' : 'does not match'
    }

    @Unroll("escaping: #globString #matches #candidate")
    def 'escaping tests'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString | candidate | expected
        'fo\\*'    | 'fo*'     | true
        'fo\\*'    | 'fot'     | false
        'fo\\?'    | 'fo?'     | true
        'fo\\?'    | 'foo'     | false
        matches = expected ? 'matches' : 'does not match'
    }

    @Unroll("char groups: #globString #matches #candidate")
    def 'char group tests'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString   | candidate | expected
        'f[aeo]o'    | 'fao'     | true
        'f[aeo]o'    | 'feo'     | true
        'f[aeo]o'    | 'foo'     | true
        'f[aeo]o'    | 'fuo'     | false
        'f[\\*o]o'   | 'f*o'     | true
        'f[\\*o]o'   | 'foo'     | true
        'f[\\*o]o'   | 'f\\o'    | false
        'f[ri]o[di]' | 'frod'    | true
        'f[ri]o[di]' | 'fiod'    | true
        'f[ri]o[di]' | 'froi'    | true
        'f[ri]o[di]' | 'fioi'    | true
        matches = expected ? 'matches' : 'does not match'
    }

    @Unroll("combined: #globString #matches #candidate")
    def 'combined tests'() {
        setup:
        glob = new InMemoryCache.Glob(globString)

        expect:
        glob.toPattern().matcher(candidate).matches() == expected

        where:
        globString                       | candidate         | expected
        '*ba?one[yi] pon[yi\\?]'         | '  balonei pon?'  | true
        '*ba?one[yi] pon[yi\\?]'         | ' baloney poni'   | true
        '*ba?one[yi] pon[yi\\?]'         | 'balonei pony'    | true
        '\\*ba?one[\\\\yi] \\pon[yi\\?]' | '*barone\\ pon?'  | true
        '\\*ba?one[\\\\yi] \\pon[yi\\?]' | ' *barone\\ pon?' | false
        '\\dontcare[\\]\\'               | 'dontcare[]'      | true
        '\\dontcare[\\]\\'               | 'dontcare['       | false
        'dontcare[\\'                    | 'dontcare['       | true
        'dontcare\\'                     | 'dontcare'        | true
        matches = expected ? 'matches' : 'does not match'
    }

}
