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

import dao.versions.ItemFactory;
import exceptions.GroundException;
import models.models.Structure;
import models.models.Tag;
import models.versions.Item;

import java.util.List;
import java.util.Map;

public interface StructureFactory extends ItemFactory<Structure> {

  Structure create(String name, String sourceKey, Map<String, Tag> tags) throws GroundException;

  @Override
  default Class<Structure> getType() {
    return Structure.class;
  }

  @Override
  Structure retrieveFromDatabase(String sourceKey) throws GroundException;

  @Override
  Structure retrieveFromDatabase(long id) throws GroundException;

  @Override
  void update(long itemId, long childId, List<Long> parentIds) throws GroundException;

  @Override
  List<Long> getLeaves(String name) throws GroundException;
}
