/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.ground.api.models.neo4j;

import edu.berkeley.ground.api.models.Node;
import edu.berkeley.ground.api.models.NodeFactory;
import edu.berkeley.ground.api.versions.GroundType;
import edu.berkeley.ground.api.versions.neo4j.Neo4jItemFactory;
import edu.berkeley.ground.db.DBClient.GroundDBConnection;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.db.Neo4jClient;
import edu.berkeley.ground.db.Neo4jClient.Neo4jConnection;
import edu.berkeley.ground.exceptions.EmptyResultException;
import edu.berkeley.ground.exceptions.GroundException;
import edu.berkeley.ground.util.IdGenerator;

import org.neo4j.driver.internal.value.StringValue;
import org.neo4j.driver.v1.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Neo4jNodeFactory extends NodeFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jNodeFactory.class);
  private Neo4jClient dbClient;
  private Neo4jItemFactory itemFactory;

  private IdGenerator idGenerator;

  public Neo4jNodeFactory(Neo4jItemFactory itemFactory, Neo4jClient dbClient, IdGenerator idGenerator) {
    this.dbClient = dbClient;
    this.itemFactory = itemFactory;
    this.idGenerator = idGenerator;
  }

  public Node create(String name) throws GroundException {
    Neo4jConnection connection = this.dbClient.getConnection();

    try {
      long uniqueId = this.idGenerator.generateItemId();

      List<DbDataContainer> insertions = new ArrayList<>();
      insertions.add(new DbDataContainer("name", GroundType.STRING, name));
      insertions.add(new DbDataContainer("id", GroundType.LONG, uniqueId));

      connection.addVertex("Node", insertions);

      connection.commit();
      LOGGER.info("Created node " + name + ".");

      return NodeFactory.construct(uniqueId, name);
    } catch (GroundException e) {
      connection.abort();

      throw e;
    }
  }

  public List<Long> getLeaves(String name) throws GroundException {
    Node node = this.retrieveFromDatabase(name);

    Neo4jConnection connection = this.dbClient.getConnection();
    List<Long> leaves = this.itemFactory.getLeaves(connection, node.getId());
    connection.commit();

    return leaves;
  }

  public Node retrieveFromDatabase(String name) throws GroundException {
    Neo4jConnection connection = this.dbClient.getConnection();

    try {
      List<DbDataContainer> predicates = new ArrayList<>();
      predicates.add(new DbDataContainer("name", GroundType.STRING, name));

      Record record;
      try {
        record = connection.getVertex("Node", predicates);
      } catch (EmptyResultException eer) {
        throw new GroundException("No Node found with name " + name + ".");
      }

      long id = record.get("v").asNode().get("id").asLong();

      connection.commit();
      LOGGER.info("Retrieved node " + name + ".");

      return NodeFactory.construct(id, name);
    } catch (GroundException e) {
      connection.abort();

      throw e;
    }
  }

  public void update(GroundDBConnection connection, long itemId, long childId, List<Long> parentIds) throws GroundException {
    this.itemFactory.update(connection, itemId, childId, parentIds);
  }
}
