/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import wf.bitcoin.javabitcoindrpcclient.MapWrapperType;
import wf.bitcoin.krotjson.HexCoder;

/**
 * Unfortunately, wf.bitcoin.javabitcoinrpcclient.MapWrapper is package-private.
 */
public class MapWrapper implements MapWrapperType {
  private static final long SECONDS_TO_MILLISECONDS = 1000L;

  private final Map<String, ?> map;

  public MapWrapper(Map<String, ?> map) {
    this.map = map;
  }

  @Override
  public Boolean mapBool(String s) {
    return this.map.containsKey(s) ? (Boolean) this.map.get(s) : null;
  }

  public Short mapShort(String s) {
    return this.map.containsKey(s) ? ((Number) this.map.get(s)).shortValue() : null;
  }

  @Override
  public Integer mapInt(String s) {
    return this.map.containsKey(s) ? ((Number) this.map.get(s)).intValue() : null;
  }

  @Override
  public Long mapLong(String s) {
    return this.map.containsKey(s) ? ((Number) this.map.get(s)).longValue() : null;
  }

  @Override
  public String mapStr(String s) {
    return this.map.containsKey(s) ? this.map.get(s).toString() : null;
  }

  @Override
  public Date mapDate(String s) {
    return this.map.containsKey(s)
        ? new Date(((Number) this.map.get(s)).longValue() * SECONDS_TO_MILLISECONDS)
        : null;
  }

  @Override
  public BigDecimal mapBigDecimal(String s) {
    return this.map.containsKey(s) ? new BigDecimal(this.map.get(s).toString()) : null;
  }

  @Override
  public byte[] mapHex(String s) {
    return this.map.containsKey(s) ? HexCoder.decode(this.map.get(s).toString()) : null;
  }

  @Override
  public String toString() {
    return String.valueOf(this.map);
  }
}
