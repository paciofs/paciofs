/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.logging;

import ch.qos.logback.core.PropertyDefinerBase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public class LogbackPropertyDefiners {
  private LogbackPropertyDefiners() {}

  public static class ConfigVarWithDefaultValue extends PropertyDefinerBase {
    private static Config config;

    private String configVar;
    private String defaultValue;

    public ConfigVarWithDefaultValue() {
      this.configVar = null;
      this.defaultValue = null;
    }

    public static void setConfig(Config config) {
      ConfigVarWithDefaultValue.config = config;
    }

    public void setConfigVar(String configVar) {
      this.configVar = configVar;
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public String getPropertyValue() {
      if (config == null) {
        throw new IllegalStateException(
            "config was not set, initialize this PropertyDefiner before using it");
      }

      if (this.configVar == null || "".equals(this.configVar)) {
        throw new IllegalStateException("configVar not specified");
      }

      try {
        return config.getString(this.configVar);
      } catch (ConfigException.Missing e) {
        if (this.defaultValue == null) {
          throw new IllegalStateException("defaultValue not specified");
        }
      }

      return this.defaultValue;
    }
  }

  public static class EnvVarWithDefaultValue extends PropertyDefinerBase {
    private String envVar;
    private String defaultValue;

    public EnvVarWithDefaultValue() {
      this.envVar = null;
      this.defaultValue = null;
    }

    public void setEnvVar(String envVar) {
      this.envVar = envVar;
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public String getPropertyValue() {
      if (this.envVar == null || "".equals(this.envVar)) {
        throw new IllegalStateException("envVar not specified");
      }

      final String envValue = System.getenv(this.envVar);
      if (envValue == null) {
        if (this.defaultValue == null) {
          throw new IllegalStateException("defaultValue not specified");
        }

        return this.defaultValue;
      }

      return envValue;
    }
  }
}
