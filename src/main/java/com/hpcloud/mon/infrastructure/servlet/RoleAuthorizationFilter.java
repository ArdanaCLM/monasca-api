package com.hpcloud.mon.infrastructure.servlet;

import com.hpcloud.mon.resource.exception.Exceptions;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import static com.hpcloud.mon.infrastructure.servlet.PostAuthenticationFilter.X_MONASCA_AGENT;

public class RoleAuthorizationFilter implements ContainerRequestFilter {

    @Context
    private HttpServletRequest httpServletRequest;
    private static final String VALID_MONASCA_AGENT_PATH = "/v2.0/metrics";

    @Override
    public ContainerRequest filter(ContainerRequest containerRequest) {
        String method = containerRequest.getMethod();
        Object isAgent = httpServletRequest.getAttribute(X_MONASCA_AGENT);
        String pathInfo = httpServletRequest.getPathInfo();

        if (isAgent != null) {
            if (!pathInfo.equals(VALID_MONASCA_AGENT_PATH)) {
                throw Exceptions.badRequest("Tenant is missing a required role to perform this request");
            } else if (pathInfo.equals(VALID_MONASCA_AGENT_PATH) && !method.equals("POST")) {
                throw Exceptions.badRequest("Tenant is missing a required role to perform this request");
            }
        }
        return containerRequest;
    }
}
