/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

public class PacioFsOptions {
  public static final String HTTP_BIND_HOSTNAME = "paciofs.http.bind-hostname";
  public static final String HTTP_BIND_PORT = "paciofs.http.bind-port";

  public static final String HTTPS_BIND_HOSTNAME = "paciofs.https.bind-hostname";
  public static final String HTTPS_BIND_PORT = "paciofs.https.bind-port";
  public static final String HTTPS_CA_CERT_PATH = "paciofs.https.certs.ca.path";
  public static final String HTTPS_CA_CERT_PASS_PATH = "paciofs.https.certs.ca.pass-path";
  public static final String HTTPS_SERVER_CERT_PATH = "paciofs.https.certs.server.path";
  public static final String HTTPS_SERVER_CERT_PASS_PATH = "paciofs.https.certs.server.pass-path";

  private PacioFsOptions() {}
}
