package com.netflix.spinnaker.gate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class MultiAuthSupport {

  @Value("${default.legacy-server-port-auth:true}")
  private boolean legacyServerPortAuth;

  @Value("${default.legacy-server-port:-1}")
  private int legacyServerPort;

  @Bean
  RequestMatcherProvider multiAuthRequestMatcherProvider(ApplicationContext applicationContext) {
    return new RequestMatcherProvider() {
      @Override
      public RequestMatcher requestMatcher() {
        if (applicationContext instanceof WebServerApplicationContext) {
          final WebServerApplicationContext ctx = (WebServerApplicationContext) applicationContext;
          return req -> {
            if (legacyServerPortAuth && legacyServerPort == req.getLocalPort()) {
              return true;
            }
            // we have to do this per request because at bean-creation time the WebServer has not
            // yet been created
            final WebServer webServer = ctx.getWebServer();
            if (webServer instanceof TomcatWebServer) {
              if (((TomcatWebServer) webServer).getTomcat().getService().findConnectors().length
                  > 1) {
                final int localPort = req.getLocalPort();
                final int defaultPort = webServer.getPort();
                return localPort == defaultPort;
              }
            }
            return true;
          };
        }

        return AnyRequestMatcher.INSTANCE;
      }
    };
  }
}
