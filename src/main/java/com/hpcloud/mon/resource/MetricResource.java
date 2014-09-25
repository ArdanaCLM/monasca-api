/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.hpcloud.mon.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.hpcloud.mon.app.MetricService;
import com.hpcloud.mon.app.command.CreateMetricCommand;
import com.hpcloud.mon.app.validation.Validation;
import com.hpcloud.mon.common.model.Services;
import com.hpcloud.mon.common.model.metric.Metric;
import com.hpcloud.mon.common.model.metric.MetricDefinition;
import com.hpcloud.mon.domain.model.metric.MetricDefinitionRepository;
import com.hpcloud.mon.resource.exception.Exceptions;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Metric resource implementation.
 */
@Path("/v2.0/metrics")
@Api(value = "/v2.0/metrics", description = "Operations for accessing metrics")
public class MetricResource {
  private static final String MONITORING_DELEGATE_ROLE = "monitoring-delegate";
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    private final MetricService service;
  private final MetricDefinitionRepository metricRepo;

  @Inject
  public MetricResource(MetricService service, MetricDefinitionRepository metricRepo) {
    this.service = service;
    this.metricRepo = metricRepo;
  }

  @POST
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create metrics")
  public void create(@Context UriInfo uriInfo, @HeaderParam("X-Tenant-Id") String tenantId,
      @HeaderParam("X-Roles") String roles, @QueryParam("tenant_id") String crossTenantId,
      @Valid CreateMetricCommand[] commands) {
    boolean isDelegate =
            !Strings.isNullOrEmpty(roles)
                    && COMMA_SPLITTER.splitToList(roles).contains(MONITORING_DELEGATE_ROLE);
    List<Metric> metrics = new ArrayList<>(commands.length);
    for (CreateMetricCommand command : commands) {
      if (!isDelegate) {
        if (command.dimensions != null) {
          String service = command.dimensions.get(Services.SERVICE_DIMENSION);
          if (service != null && Services.isReserved(service))
            throw Exceptions.forbidden("Project %s cannot POST metrics for the hpcs service",
                tenantId);
        }
        if (!Strings.isNullOrEmpty(crossTenantId))
          throw Exceptions.forbidden("Project %s cannot POST cross tenant metrics", tenantId);
      }

      command.validate();
      metrics.add(command.toMetric());
    }

    service.create(metrics, tenantId, crossTenantId);
  }

  @GET
  @Timed
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get metrics", response = MetricDefinition.class,
      responseContainer = "List")
  public List<MetricDefinition> getMetrics(@HeaderParam("X-Tenant-Id") String tenantId,
      @QueryParam("name") String name, @QueryParam("dimensions") String dimensionsStr)
      throws Exception {
    Map<String, String> dimensions =
        Strings.isNullOrEmpty(dimensionsStr) ? null : Validation.parseAndValidateNameAndDimensions(
            name, dimensionsStr);
    return metricRepo.find(tenantId, name, dimensions);
  }
}
