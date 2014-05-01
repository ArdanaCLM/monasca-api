/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hpcloud.mon.infrastructure.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hpcloud.mon.resource.exception.Exceptions;
import com.hpcloud.mon.resource.exception.Exceptions.FaultType;

/**
 * Authenticates requests using header information from the CsMiddleware. Provides the X-TENANT-ID
 * servlet attribute as a request header. Intended to be added to a servlet filter chain after the
 * CsMiddleware TokenAuth filter.
 */
public class PreAuthenticationFilter implements Filter {
  private static final Logger LOG = LoggerFactory.getLogger(PreAuthenticationFilter.class);

  static class ErrorCapturingServletResponseWrapper extends HttpServletResponseWrapper {
    private int statusCode;
    private String errorMessage;
    private Exception exception;

    public ErrorCapturingServletResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void sendError(int statusCode) throws IOException {
      this.statusCode = statusCode;
    }

    @Override
    public void sendError(int statusCode, String msg) throws IOException {
      this.statusCode = statusCode;
      errorMessage = msg;
    }

    void sendError(int statusCode, String msg, Exception exception) throws IOException {
      sendError(statusCode, msg);
      this.exception = exception;
    }
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    HttpServletResponse res = (HttpServletResponse) response;
    ErrorCapturingServletResponseWrapper responseWrapper = new ErrorCapturingServletResponseWrapper(
        res);

    ServletOutputStream out = null;

    try {
      out = res.getOutputStream();
      chain.doFilter(request, responseWrapper);
      if (responseWrapper.statusCode != 401)
        return;

    } catch (Exception e) {
      LOG.error("Error while executing pre authentication filter", e);
    }

    try {
      res.setContentType(MediaType.APPLICATION_JSON);
      res.setStatus(responseWrapper.statusCode);
      String output = Exceptions.buildLoggedErrorMessage(FaultType.UNAUTHORIZED,
          responseWrapper.errorMessage, null, responseWrapper.exception);
      out.print(output);
    } catch (Exception e) {
      LOG.error("Error while writing failed authentication HTTP response", e);
    } finally {
      if (out != null)
        try {
          out.close();
        } catch (IOException ignore) {
        }
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }
}
