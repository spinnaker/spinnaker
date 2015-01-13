/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.bench

import org.springframework.stereotype.Component

import java.nio.charset.Charset

@Component
class EndpointMonitor {

  long now() {
    System.nanoTime()
  }

  EndpointMetrics call(URI uri) {
    EndpointMetrics metrics = new EndpointMetrics(uri: uri, startTime: now())
    try {
      Socket s = new Socket(uri.host, uri.port)
      metrics.timings.connect = now()

      InputStream is = s.getInputStream()
      OutputStream os = s.getOutputStream()
      def request = [
        "GET ${uri.toString()} HTTP/1.1",
        "Host: ${uri.host}",
        "Accept: application/json",
        "Accept-Encoding: gzip",
        "Connection: close",

        "\r\n"
      ]
      os.write(request.join('\r\n').bytes)
      os.flush()

      byte[] buf = new byte[16 * 1024]
      int bytesRead = is.read(buf)
      metrics.timings.firstBytes = now()
      int totalBytes = 0
      byte searchByte = '\n'
      for (int i = 0; i < bytesRead; i++) {
        if (buf[i] == searchByte) {
          String[] respLine = new String(buf, 0, i, Charset.forName('US-ASCII')).split(' ')
          metrics.httpResponseCode = Integer.parseInt(respLine[1])
          break
        }
      }

      while (bytesRead != -1) {
        totalBytes += bytesRead
        bytesRead = is.read(buf)
      }
      metrics.timings.allBytes = now()
      metrics.totalBytes = totalBytes
      is.close()
      os.close()
      s.close()
    } catch (Throwable t) {
      metrics.exception = t
    }
    metrics
  }
}
