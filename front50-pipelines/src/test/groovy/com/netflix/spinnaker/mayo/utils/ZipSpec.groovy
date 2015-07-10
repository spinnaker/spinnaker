/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.mayo.utils

import spock.lang.Specification

class ZipSpec extends Specification {

    void 'can compress and uncompress correctly'() {

        given:
        String text = '''Put a bird on it food truck Blue Bottle, High Life church-key fap Cosby sweater. \
Lo-fi pour-over PBR&B kale chips scenester Neutra. Cardigan Wes Anderson seitan Shoreditch McSweeney's. \
Sartorial beard pork belly, fingerstache keffiyeh squid readymade kale chips master cleanse fanny pack Shoreditch. \
Keffiyeh four loko you probably haven't heard of them, XOXO Cosby sweater church-key stumptown lomo ethnic occupy \
Etsy swag meggings semiotics. Wes Anderson pork belly small batch, tattooed locavore pop-up vinyl artisan authentic \
shabby chic aesthetic. Chia Etsy selfies before they sold out Carles chambray.'''

        expect:
        Zip.decompress(Zip.compress(text)) == text
    }

}
