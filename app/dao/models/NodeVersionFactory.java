/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dao.models;

import exceptions.GroundException;
import models.models.NodeVersion;
import models.models.Tag;

import java.util.List;
import java.util.Map;

public interface NodeVersionFactory extends RichVersionFactory<NodeVersion> {
  NodeVersion create(Map<String, Tag> tags,
                                     long structureVersionId,
                                     String reference,
                                     Map<String, String> referenceParameters,
                                     long nodeId,
                                     List<Long> parentIds) throws GroundException;

  @Override
  default Class<NodeVersion> getType() {
    return NodeVersion.class;
  }

  @Override
  NodeVersion retrieveFromDatabase(long id) throws GroundException;
}
