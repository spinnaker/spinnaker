package com.netflix.spinnaker.gate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
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

  /**
   * From https://github.com/spring-projects/spring-security/issues/11055#issuecomment-1098061598,
   * to fix java.lang.UnsupportedOperationException: public abstract int
   * javax.servlet.ServletRequest.getLocalPort() is not supported when processing error responses
   * for spring boot >= 2.6.4 and <= 3.0.0.
   *
   * <p>https://github.com/spring-projects/spring-boot/commit/71acc90da8 removed
   * ErrorPageSecurityFilterConfiguration (which registered the errorPageSecurityInterceptor bean of
   * type FilterRegistrationBean<ErrorPageSecurityFilter> for 2.7.x), but added
   * ErrorPageSecurityFilterConfiguration to SpringBootWebSecurityConfiguration which registered a
   * bean named errorPageSecurityFilter of the same type.
   *
   * <p>https://github.com/spring-projects/spring-boot/commit/4bd3534b7d91f922ad903a75beb19b6bdca39e5c
   * reverted those changes for 3.0.0-M4 and 3.0.0-M5.
   *
   * <p>https://github.com/spring-projects/spring-boot/commit/cedd553b836d97a04d769322771bc1a8499e7282
   * removed ErrorPageSecurityFilter and the corresponding filter for good in 3.0.0-RC1.
   *
   * <p>Deleting a bean by name fails if the bean doesn't exist.
   */
  @Bean
  public static BeanFactoryPostProcessor removeErrorSecurityFilter() {
    return beanFactory ->
        ((DefaultListableBeanFactory) beanFactory).removeBeanDefinition("errorPageSecurityFilter");
  }

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
