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
package com.hpcloud.mon.resource;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import com.hpcloud.mon.domain.model.version.Version;
import com.hpcloud.mon.domain.model.version.VersionRepository;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

/**
 * Version resource implementation.
 */
@Path("/")
@Api(value = "/", description = "Operations for accessing versions")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {
  private final VersionRepository repository;

  @Inject
  public VersionResource(VersionRepository repository) {
    this.repository = repository;
  }

  @GET
  @Timed
  @ApiOperation(value = "Get versions", response = Version.class, responseContainer = "List")
  public List<Version> list(@Context UriInfo uriInfo) {
    return Links.hydrate(repository.find(), uriInfo);
  }

  @GET
  @Timed
  @Path("{version_id}")
  @ApiOperation(value = "Get version", response = Version.class)
  public Version get(@Context UriInfo uriInfo, @PathParam("version_id") String versionId) {
    return Links.hydrate(repository.findById(versionId), uriInfo, true);
  }
}
