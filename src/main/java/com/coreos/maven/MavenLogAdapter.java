/*
 * Copyright (c) 2015 CoreOS Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.coreos.maven;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

public class MavenLogAdapter extends MarkerIgnoringBase {
  private static final long serialVersionUID = 1L;

  private final Log log;
  private final boolean traceEnabled;

  public MavenLogAdapter(Log log, boolean traceEnabled) {
    this.log = log;
    this.traceEnabled = traceEnabled;
  }

  @Override
  public String getName() {
    return "maven";
  }

  @Override
  public boolean isTraceEnabled() {
    return traceEnabled;
  }

  @Override
  public void trace(String msg) {
    if (traceEnabled) {
      log.debug(msg);
    }
  }

  @Override
  public void trace(String format, Object arg) {
    if (traceEnabled) {
      log.debug(format(format, arg));
    }
  }

  @Override
  public void trace(String format, Object arg1, Object arg2) {
    if (traceEnabled) {
      log.debug(format(format, arg1, arg2));
    }

  }

  @Override
  public void trace(String format, Object... arguments) {
    if (traceEnabled) {
      log.debug(format(format, arguments));
    }
  }

  @Override
  public void trace(String msg, Throwable t) {
    if (traceEnabled) {
      log.debug(msg, t);
    }
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public void debug(String msg) {
    log.debug(msg);
  }

  @Override
  public void debug(String format, Object arg) {
    if (log.isDebugEnabled()) {
      log.debug(format(format, arg));
    }
  }

  @Override
  public void debug(String format, Object arg1, Object arg2) {
    if (log.isDebugEnabled()) {
      log.debug(format(format, arg1, arg2));
    }
  }

  @Override
  public void debug(String format, Object... arguments) {
    if (log.isDebugEnabled()) {
      log.debug(format(format, arguments));
    }
  }

  @Override
  public void debug(String msg, Throwable t) {
    log.debug(msg, t);
  }

  @Override
  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  @Override
  public void info(String msg) {
    log.info(msg);
  }

  @Override
  public void info(String format, Object arg) {
    if (log.isInfoEnabled()) {
      log.info(format(format, arg));
    }
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    if (log.isInfoEnabled()) {
      log.info(format(format, arg1, arg2));
    }

  }

  @Override
  public void info(String format, Object... arguments) {
    if (log.isInfoEnabled()) {
      log.info(format(format, arguments));
    }

  }

  @Override
  public void info(String msg, Throwable t) {
    log.info(msg, t);
  }

  @Override
  public boolean isWarnEnabled() {
    return log.isWarnEnabled();
  }

  @Override
  public void warn(String msg) {
    log.warn(msg);
  }

  @Override
  public void warn(String format, Object arg) {
    if (log.isWarnEnabled()) {
      log.warn(format(format, arg));
    }
  }

  @Override
  public void warn(String format, Object... arguments) {
    if (log.isWarnEnabled()) {
      log.warn(format(format, arguments));
    }
  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    if (log.isWarnEnabled()) {
      log.warn(format(format, arg1, arg2));
    }

  }

  @Override
  public void warn(String msg, Throwable t) {
    log.warn(msg, t);
  }

  @Override
  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  @Override
  public void error(String msg) {
    log.error(msg);
  }

  @Override
  public void error(String format, Object arg) {
    if (log.isErrorEnabled()) {
      log.error(format(format, arg));
    }
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    if (log.isErrorEnabled()) {
      log.error(format(format, arg1, arg2));
    }

  }

  @Override
  public void error(String format, Object... arguments) {
    if (log.isErrorEnabled()) {
      log.error(format(format, arguments));
    }

  }

  @Override
  public void error(String msg, Throwable t) {
    log.error(msg, t);
  }

  private CharSequence format(String messagePattern, Object... arguments) {
    return MessageFormatter.format(messagePattern, arguments).getMessage();
  }

  private CharSequence format(String messagePattern, Object arg1, Object arg2) {
    return MessageFormatter.format(messagePattern, arg1, arg2).getMessage();
  }

  private CharSequence format(String messagePattern, Object arg) {
    return MessageFormatter.format(messagePattern, arg).getMessage();
  }

}
