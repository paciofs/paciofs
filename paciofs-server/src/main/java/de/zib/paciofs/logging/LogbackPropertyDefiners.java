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

  private abstract static class AbstractVarWithDefaultValue extends PropertyDefinerBase {
    private String var;
    private String defaultValue;

    public void setVar(String var) {
      this.var = var;
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    protected String getVar() {
      if (this.var == null || "".equals(this.var)) {
        throw new IllegalStateException("var not specified");
      }

      return this.var;
    }

    protected String getDefaultValue() {
      if (this.defaultValue == null) {
        throw new IllegalStateException("defaultValue not specified");
      }

      return this.defaultValue;
    }
  }

  public static class ConfigVarWithDefaultValue extends AbstractVarWithDefaultValue {
    private static Config config;

    public ConfigVarWithDefaultValue() {}

    public static void setConfig(Config config) {
      ConfigVarWithDefaultValue.config = config;
    }

    /* Must redeclare setters so Logback finds them */

    @Override
    public void setVar(String var) {
      super.setVar(var);
    }

    @Override
    public void setDefaultValue(String defaultValue) {
      super.setDefaultValue(defaultValue);
    }

    @Override
    public String getPropertyValue() {
      if (config == null) {
        throw new IllegalStateException(
            "config was not set, initialize this PropertyDefiner before using it");
      }

      try {
        return config.getString(this.getVar());
      } catch (ConfigException.Missing e) {
        return this.getDefaultValue();
      }
    }
  }

  public static class EnvVarWithDefaultValue extends AbstractVarWithDefaultValue {
    public EnvVarWithDefaultValue() {}

    /* Must redeclare setters so Logback finds them */

    @Override
    public void setVar(String var) {
      super.setVar(var);
    }

    @Override
    public void setDefaultValue(String defaultValue) {
      super.setDefaultValue(defaultValue);
    }

    @Override
    public String getPropertyValue() {
      final String envValue = System.getenv(this.getVar());
      if (envValue == null) {
        return getDefaultValue();
      }

      return envValue;
    }
  }
}
