/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Markers {
  public static final Marker CONFIGURATION = MarkerFactory.getMarker("CONFIGURATION");
  public static final Marker EXCEPTION = MarkerFactory.getMarker("EXCEPTION");

  private Markers() {}
}
