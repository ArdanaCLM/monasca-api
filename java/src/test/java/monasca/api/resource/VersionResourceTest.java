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

package monasca.api.resource;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import monasca.api.domain.exception.EntityNotFoundException;
import monasca.api.domain.model.common.Link;
import monasca.api.domain.model.version.Version;
import monasca.api.domain.model.version.Version.VersionStatus;
import monasca.api.domain.model.version.VersionRepository;
import com.sun.jersey.api.client.GenericType;

@Test
public class VersionResourceTest extends AbstractMonApiResourceTest {
  private Version version;
  private VersionRepository repo;

  @Override
  protected void setupResources() throws Exception {
    super.setupResources();
    version = new Version("v2.0", VersionStatus.CURRENT, new DateTime(1355253328));
    version.setLinks(Arrays.asList(new Link("self",
        "https://cloudsvc.example.com/v2.0")));

    repo = mock(VersionRepository.class);
    when(repo.findById(eq("v2.0"))).thenReturn(version);
    when(repo.find()).thenReturn(Arrays.asList(version));
    addResources(new VersionResource(repo));
  }

  public void shouldList() {
    List<Version> versions = client().resource("/").get(new GenericType<List<Version>>() {});
    assertEquals(versions, Arrays.asList(version));
    verify(repo).find();
  }

  public void shouldGet() {
    assertEquals(client().resource("/v2.0").get(Version.class), version);
    verify(repo).findById(eq("v2.0"));
  }

  public void should404OnGetInvalid() {
    doThrow(new EntityNotFoundException("")).when(repo).findById(anyString());

    try {
      client().resource("/v9.9").get(Version.class);
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("404"));
    }
  }

  public void should500OnInternalException() {
    doThrow(new RuntimeException("")).when(repo).find();

    try {
      client().resource("/").get(new GenericType<List<Version>>() {});
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("500"));
    }
  }
}
