/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

public class PacioFsOptions {
  public static final String BASEDIR = "paciofs.base-dir";

  public static final String HTTP_BIND_HOSTNAME_KEY = "paciofs.http.bind-hostname";
  public static final String HTTP_BIND_PORT_KEY = "paciofs.http.bind-port";

  public static final String HTTPS_BIND_HOSTNAME_KEY = "paciofs.https.bind-hostname";
  public static final String HTTPS_BIND_PORT_KEY = "paciofs.https.bind-port";
  public static final String HTTPS_CA_CERT_PATH_KEY = "paciofs.https.certs.ca.path";
  public static final String HTTPS_CA_CERT_PASS_PATH_KEY = "paciofs.https.certs.ca.pass-path";
  public static final String HTTPS_SERVER_CERT_PATH_KEY = "paciofs.https.certs.server.path";
  public static final String HTTPS_SERVER_CERT_PASS_PATH_KEY =
      "paciofs.https.certs.server.pass-path";

  public static final String MULTICHAIN_CLIENT_KEY = "paciofs.multichain-client";

  private PacioFsOptions() {}
}
