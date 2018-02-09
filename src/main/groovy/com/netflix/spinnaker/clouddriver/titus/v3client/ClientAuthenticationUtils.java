package com.netflix.spinnaker.clouddriver.titus.v3client;

import com.netflix.metatron.ipc.security.MetatronKeyManagerFactory;
import com.netflix.metatron.ipc.security.MetatronTrustManagerFactory;

import com.netflix.netty.jettyalpn.ShadedJettyAlpnSslContext;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.InvalidAlgorithmParameterException;

/**
 * Helper class that builds Metatron/Netty SslContext, and injects caller id into requests.
 */
public class ClientAuthenticationUtils {

  public static String CALLER_ID_HEADER = "X-Titus-CallerId";
  public static Metadata.Key<String> CALLER_ID_KEY = Metadata.Key.of(CALLER_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

  public static SslContext newSslContext(String applicationName) {
    try {
      KeyManagerFactory kmf = new MetatronKeyManagerFactory();
      kmf.init(MetatronKeyManagerFactory.clientParameters());
      TrustManagerFactory tmf = new MetatronTrustManagerFactory();
      tmf.init(MetatronTrustManagerFactory.clientParameters(applicationName));
      return new ShadedJettyAlpnSslContext(kmf, tmf, true, null);
    } catch (InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }
  }

  public static <STUB extends AbstractStub<STUB>> STUB attachCallerId(STUB serviceStub, String callerId) {
    Metadata metadata = new Metadata();
    metadata.put(CALLER_ID_KEY, callerId);
    return serviceStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
  }
}
