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

/**
 * Version resource implementation.
 * 
 * @author Jonathan Halterman
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {
  private final VersionRepository repository;

  @Inject
  public VersionResource(VersionRepository repository) {
    this.repository = repository;
  }

  @GET
  @Timed
  public List<Version> list(@Context UriInfo uriInfo) {
    return Links.hydrate(repository.find(), uriInfo);
  }

  @GET
  @Timed
  @Path("{version_id}")
  public Version get(@Context UriInfo uriInfo, @PathParam("version_id") String versionId) {
    return Links.hydrate(repository.findById(versionId), uriInfo);
  }
}
