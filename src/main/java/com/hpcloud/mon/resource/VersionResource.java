package com.hpcloud.mon.resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import com.hpcloud.mon.app.representation.VersionRepresentation;
import com.hpcloud.mon.app.representation.VersionsRepresentation;
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
  public VersionsRepresentation list(@Context UriInfo uriInfo) {
    return new VersionsRepresentation(Links.hydrate(repository.find(), uriInfo));
  }

  @GET
  @Timed
  @Path("{version_id}")
  public VersionRepresentation get(@Context UriInfo uriInfo,
      @PathParam("version_id") String versionId) {
    return new VersionRepresentation(Links.hydrate(repository.findById(versionId), uriInfo));
  }
}
