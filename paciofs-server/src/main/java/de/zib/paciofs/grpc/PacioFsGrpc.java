/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.grpc;

import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.UseHttp2;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;
import akka.stream.Materializer;
import akka.stream.TLSClientAuth;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.TextFormat;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacioFsGrpc {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsGrpc.class);

  private PacioFsGrpc() {}

  /**
   * Binds handlers to an HTTP port.
   * @param handlers handlers to bind
   * @param http Http instance to use
   * @param hostname bind to this hostname
   * @param port bind to this port
   * @param materializer Akka materializer to use
   */
  public static void bindAndHandleAsyncHttp(
      Function<HttpRequest, CompletionStage<HttpResponse>> handlers, Http http, String hostname,
      int port, Materializer materializer) {
    http.bindAndHandleAsync(
            handlers, ConnectHttp.toHost(hostname, port, UseHttp2.always()), materializer)
        .thenAccept(binding -> LOG.info("gRPC HTTP server bound to: {}", binding.localAddress()));
  }

  /**
   * Binds handlers to an HTTPS port.
   * @param handlers handlers to bind
   * @param http Http instance to use
   * @param hostname bind to this hostname
   * @param port bind to this port
   * @param materializer Akka materializer to use
   * @param httpsConnectionContext the HTTPS context to use
   */
  public static void bindAndHandleAsyncHttps(
      Function<HttpRequest, CompletionStage<HttpResponse>> handlers, Http http, String hostname,
      int port, Materializer materializer, HttpsConnectionContext httpsConnectionContext) {
    http.bindAndHandleAsync(handlers,
            ConnectHttp.toHostHttps(hostname, port).withCustomHttpsContext(httpsConnectionContext),
            materializer)
        .thenAccept(binding -> LOG.info("gRPC HTTPS server bound to: {}", binding.localAddress()));
  }

  /**
   * Create an HTTPS context.
   * @param serverCertPath path to the server certificate p12 file
   * @param serverCertPassPath path to the file containing the password for serverCertPath
   * @param caCertPath path to the CA certificate p12 file (pass null to use the system default)
   * @param caCertPassPath path to the file containing the password for caCertPath
   * @return the HTTPS connection context using the server key and CA trust
   * @throws GeneralSecurityException if an error occurs setting up the key and trust managers
   * @throws IOException if any of the above files cannot be found or read
   */
  public static HttpsConnectionContext httpsConnectionContext(
      String serverCertPath, String serverCertPassPath, String caCertPath, String caCertPassPath)
      throws GeneralSecurityException, IOException {
    final String algorithm = "SunX509";
    final String protocol = "TLS";
    final String type = "PKCS12";

    // initialize key manager (contains our certificates)
    final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
    char[] password = readPasswordFromFile(serverCertPassPath);
    keyManagerFactory.init(readKeyStoreFromFile(serverCertPath, password, type), password);

    // get the context
    final SSLContext sslContext = SSLContext.getInstance(protocol);

    if (caCertPath != null && caCertPassPath != null) {
      // initialize trust manager (contains trusted certificates)
      final TrustManagerFactory trustManagerFactory;
      password = readPasswordFromFile(caCertPassPath);
      trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
      trustManagerFactory.init(readKeyStoreFromFile(caCertPath, password, type));

      sslContext.init(
          keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    } else {
      // tolerate missing trust manager configuration
      LOG.info("Using system trust managers");
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    }

    // build the context, requiring mutual authentication
    return ConnectionContext.https(sslContext, Optional.empty(), Optional.empty(),
        Optional.of(TLSClientAuth.need()), Optional.empty());
  }

  /**
   * Build a string representation of messages, and logs them at trace level if enabled.
   * @param log logger to use
   * @param formatString format string with placeholders to use
   * @param messages messages to convert to strings via shortDebugString
   */
  public static void traceRequest(Logger log, String formatString, AbstractMessage... messages) {
    // building the string representations is expensive, so guard it
    if (log.isTraceEnabled()) {
      final String[] messageStrings = new String[messages.length];
      for (int i = 0; i < messages.length; ++i) {
        messageStrings[i] = TextFormat.shortDebugString(messages[i]);
      }
      log.trace(formatString, messageStrings);
    }
  }

  private static KeyStore readKeyStoreFromFile(String path, char[] password, String type)
      throws GeneralSecurityException, IOException {
    final KeyStore keyStore = KeyStore.getInstance(type);
    try (InputStream p12 = new FileInputStream(path)) {
      keyStore.load(p12, password);
    }

    return keyStore;
  }

  private static char[] readPasswordFromFile(String path) throws IOException {
    final char[] password;
    try (BufferedReader passReader = new BufferedReader(
             new InputStreamReader(new FileInputStream(path), Charset.forName("UTF-8")))) {
      final String pass = passReader.readLine();
      if (pass == null) {
        throw new IllegalArgumentException(path + " is empty");
      }

      password = new char[pass.length()];
      pass.getChars(0, pass.length(), password, 0);
    }

    return password;
  }
}
