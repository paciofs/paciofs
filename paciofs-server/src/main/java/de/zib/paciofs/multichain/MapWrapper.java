/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import wf.bitcoin.javabitcoindrpcclient.MapWrapperType;
import wf.bitcoin.krotjson.HexCoder;

/**
 * Unfortunately, wf.bitcoin.javabitcoinrpcclient.MapWrapper is package-private.
 */
public class MapWrapper implements MapWrapperType, Serializable {
  private static final long serialVersionUID = 7678720626147868841L;

  private static final long SECONDS_TO_MILLISECONDS = 1000L;

  private final Map<String, ?> map;

  public MapWrapper(Map<String, ?> map) {
    this.map = map;
  }

  @Override
  public Boolean mapBool(String s) {
    return (Boolean) this.getOrThrow(s);
  }

  public Short mapShort(String s) {
    return ((Number) this.getOrThrow(s)).shortValue();
  }

  @Override
  public Integer mapInt(String s) {
    return ((Number) this.getOrThrow(s)).intValue();
  }

  @Override
  public Long mapLong(String s) {
    return ((Number) this.getOrThrow(s)).longValue();
  }

  @Override
  public String mapStr(String s) {
    return this.getOrThrow(s).toString();
  }

  @Override
  public Date mapDate(String s) {
    return new Date(((Number) this.getOrThrow(s)).longValue() * SECONDS_TO_MILLISECONDS);
  }

  @Override
  public BigDecimal mapBigDecimal(String s) {
    return new BigDecimal(this.getOrThrow(s).toString());
  }

  @Override
  public byte[] mapHex(String s) {
    return HexCoder.decode(this.getOrThrow(s).toString());
  }

  @Override
  public String toString() {
    return String.valueOf(this.map);
  }

  private Object getOrThrow(String s) {
    if (!this.map.containsKey(s)) {
      throw new IllegalArgumentException(s);
    }

    final Object o = this.map.get(s);
    if (o == null) {
      throw new NullPointerException(s);
    }

    return o;
  }
}
