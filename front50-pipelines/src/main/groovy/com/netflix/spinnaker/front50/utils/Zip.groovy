/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.utils

import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Utility to compress a string
 */
class Zip {

    static byte[] compress(String str) throws IOException {
        byte[] output = new byte[1048576]
        Deflater compresser = new Deflater()
        compresser.setInput(str.getBytes('UTF-8'))
        compresser.finish()
        compresser.deflate(output)
        compresser.end()
        output
    }

    static String decompress(byte[] bytes) throws IOException {
        Inflater decompresser = new Inflater()
        decompresser.setInput(bytes, 0, bytes.length)
        byte[] result = new byte[1048576]
        int resultLength = decompresser.inflate(result)
        decompresser.end()
        new String(result, 0, resultLength, 'UTF-8')
    }
}
