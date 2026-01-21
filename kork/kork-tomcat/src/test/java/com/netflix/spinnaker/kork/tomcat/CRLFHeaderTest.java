package com.netflix.spinnaker.kork.tomcat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {CRLFHeaderTest.HeaderTestControllerConfiguration.class})
@TestPropertySource(
    properties = {"logging.level.org.apache.coyote.http11.Http11InputBuffer = DEBUG"})
@org.springframework.boot.context.properties.EnableConfigurationProperties({
  TomcatConfigurationProperties.class
})
public class CRLFHeaderTest {
  private static final String CR = "\r";
  private static final String LF = "\n";

  private static final String HOST = "127.0.0.1";
  public static final String HTTP_1_1_PREFIX = "HTTP/1.1 ";

  @SpringBootApplication
  public static class HeaderTestControllerConfiguration {
    @Bean
    HeaderTestController headerTestController() {
      return new HeaderTestController();
    }
  }

  @RestController
  @RequestMapping("/header/test")
  static class HeaderTestController {
    @GetMapping
    public void onGetRequest() {}
  }

  @LocalServerPort private int port;

  /*
   * clientTest tests the TCP segmentation bug introduced in tomcat v9.0.31.
   * See https://bz.apache.org/bugzilla/show_bug.cgi?id=64210
   *
   * We reproduce this test by forcing a TCP segmentation in between a header that ends with a CR
   * and begins the next header with a LF. This causes tomcat to throw an exception and results in a
   * 400 status code due to invalid header parsing
   */
  @Test
  public void clientTest() throws IOException {
    Socket socket = new Socket("127.0.0.1", port);
    try {
      // set the buffer size to 1 to force a TCP segment at 1 byte
      socket.setSendBufferSize(1);
      // set TCP_NODELAY to true which prevents tcp from buffering requests
      socket.setTcpNoDelay(true);
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();

      String requestAndHeaders =
          "GET /header/test HTTP/1.1"
              + CR
              + LF
              + "Host: "
              + HOST
              + ":"
              + port
              + CR // Writes the CR to the socket and is received by the server
              + LF // This LF causes a 400
              + "User-Agent: spinnaker-test/1.0"
              + CR
              + LF
              + CR
              + LF;

      // write the first header
      out.write(requestAndHeaders.getBytes(StandardCharsets.UTF_8));
      // force a write
      out.flush();
      BufferedReader lineReader = new BufferedReader(new InputStreamReader(in));

      String line = lineReader.readLine();
      while (line != null) {
        if (line.startsWith(HTTP_1_1_PREFIX)) {
          int status = Integer.parseInt(line.substring(HTTP_1_1_PREFIX.length()).trim());
          Assertions.assertEquals(2, status / 100, "non-2xx status code returned: " + status);
        }
        line = lineReader.readLine();
      }
    } finally {
      socket.close();
    }
  }
}
