package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import org.yaml.snakeyaml.Yaml
import java.lang.ClassCastException
import javax.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus

/**
 * A filter for POST /delivery-configs which first parses YAML using snakeyaml, which supports YAML anchors
 * and aliases, then converts the output into JSON with jackson and substitutes that for the original YAML
 * in the request.
 *
 * See https://github.com/FasterXML/jackson-dataformats-text/issues/98
 */
class DeliveryConfigYamlParsingFilter : Filter {
  private val jsonMapper = ObjectMapper()

  override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
    var normalizedRequest = request

    if (request is HttpServletRequest &&
      request.method == "POST" &&
      request.contentType.toLowerCase().contains("yaml")
    ) {

      val deliveryConfigAsJson: String
      try {

        val yaml = Yaml()
        val deliveryConfig: Map<String, Any?> = yaml.load(request.inputStream)
        deliveryConfigAsJson = jsonMapper.writeValueAsString(deliveryConfig)

      } catch(e: ClassCastException) {
        (response as HttpServletResponse).status = HttpStatus.BAD_REQUEST.value()
        response.writer.print(e.message)
        response.writer.flush()
        return
      }

      normalizedRequest = object : HttpServletRequestWrapper(request) {
        override fun getContentType(): String {
          return "application/json"
        }

        override fun getInputStream(): ServletInputStream {
          return DelegateServletInputStream(deliveryConfigAsJson.byteInputStream())
        }
      }
    }

    chain?.doFilter(normalizedRequest, response)
  }

  private class DelegateServletInputStream(private val delegate: ByteArrayInputStream) : ServletInputStream() {
    override fun isFinished(): Boolean {
      return false
    }

    override fun isReady(): Boolean {
      return true
    }

    override fun setReadListener(readListener: ReadListener) {
      throw UnsupportedOperationException()
    }

    override fun read(): Int {
      return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      return delegate.read(b, off, len)
    }

    override fun read(b: ByteArray): Int {
      return delegate.read(b)
    }

    override fun skip(n: Long): Long {
      return delegate.skip(n)
    }

    override fun available(): Int {
      return delegate.available()
    }

    override fun close() {
      delegate.close()
    }

    override fun mark(readlimit: Int) {
      delegate.mark(readlimit)
    }

    override fun reset() {
      delegate.reset()
    }

    override fun markSupported(): Boolean {
      return delegate.markSupported()
    }
  }
}
