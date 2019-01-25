/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.logging;

import ch.qos.logback.core.PropertyDefinerBase;

public class LogbackPropertyDefiners {
  public static class EnvVarWithDefaultValue extends PropertyDefinerBase {
    private String envVar = null;
    private String defaultValue = null;

    public void setEnvVar(String envVar) {
      this.envVar = envVar.trim();
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public String getPropertyValue() {
      if (this.envVar == null || "".equals(envVar)) {
        return this.defaultValue;
      } else {
        String envValue = System.getenv(this.envVar);
        return envValue != null ? envValue : this.defaultValue;
      }
    }
  }
}
