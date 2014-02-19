package com.hpcloud.mon.domain.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.hpcloud.mon.domain.exception.EntityNotFoundException;
import com.hpcloud.mon.domain.model.version.Version;
import com.hpcloud.mon.domain.model.version.VersionRepository;
import com.hpcloud.mon.domain.model.version.Version.VersionStatus;

/**
 * Version repository implementation.
 * 
 * @author Jonathan Halterman
 */
public class VersionRepositoryImpl implements VersionRepository {
  private static final Version v1 = new Version("v1.0", VersionStatus.CURRENT, new Date());

  @Override
  public List<Version> find() {
    return Arrays.asList(v1);
  }

  @Override
  public Version findById(String versionId) {
    if ("v1.0".equals(versionId))
      return v1;
    throw new EntityNotFoundException("No version exists for %s", versionId);
  }
}
