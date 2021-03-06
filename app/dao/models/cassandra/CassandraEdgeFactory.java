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

package dao.models.cassandra;

import dao.models.EdgeFactory;
import dao.versions.cassandra.CassandraItemFactory;
import dao.versions.cassandra.CassandraVersionHistoryDagFactory;
import db.CassandraClient;
import db.CassandraResults;
import db.DbClient;
import db.DbDataContainer;
import exceptions.GroundException;
import models.models.Edge;
import models.models.EdgeVersion;
import models.models.Tag;
import models.versions.GroundType;
import models.versions.VersionHistoryDag;
import util.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CassandraEdgeFactory extends CassandraItemFactory<Edge> implements EdgeFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(CassandraEdgeFactory.class);
  private final CassandraClient dbClient;
  private final CassandraVersionHistoryDagFactory versionHistoryDagFactory;
  private CassandraEdgeVersionFactory edgeVersionFactory;

  private final IdGenerator idGenerator;

  /**
   * Constructor for Cassandra edge factory.
   *
   * @param dbClient the Cassandra client
   * @param idGenerator a unique ID generator
   * @param versionHistoryDagFactory a CassandraVersionHistoryDAGFactory singleton
   * @param tagFactory a CassandraTagFactory singleton
   */
  public CassandraEdgeFactory(CassandraClient dbClient,
                              CassandraVersionHistoryDagFactory versionHistoryDagFactory,
                              CassandraTagFactory tagFactory,
                              IdGenerator idGenerator) {

    super(dbClient, versionHistoryDagFactory, tagFactory);

    this.dbClient = dbClient;
    this.idGenerator = idGenerator;
    this.edgeVersionFactory = null;
    this.versionHistoryDagFactory = versionHistoryDagFactory;
  }

  public void setEdgeVersionFactory(CassandraEdgeVersionFactory edgeVersionFactory) {
    this.edgeVersionFactory = edgeVersionFactory;
  }

  /**
   * Creates and persists a new edge.
   *
   * @param name the name of the edge
   * @param sourceKey the user generated unique key for the edge
   * @param fromNodeId the id of the originating node for this edg
   * @param toNodeId the id of the destination node for this edg
   * @param tags tags on this edge
   * @return the created edge
   * @throws GroundException an error while creating or persisting the edge
   */
  @Override
  public Edge create(String name,
                     String sourceKey,
                     long fromNodeId,
                     long toNodeId,
                     Map<String, Tag> tags) throws GroundException {

    super.verifyItemNotExists(sourceKey);

    long uniqueId = this.idGenerator.generateItemId();

    super.insertIntoDatabase(uniqueId, tags);

    List<DbDataContainer> insertions = new ArrayList<>();
    insertions.add(new DbDataContainer("name", GroundType.STRING, name));
    insertions.add(new DbDataContainer("item_id", GroundType.LONG, uniqueId));
    insertions.add(new DbDataContainer("from_node_id", GroundType.LONG, fromNodeId));
    insertions.add(new DbDataContainer("to_node_id", GroundType.LONG, toNodeId));
    insertions.add(new DbDataContainer("source_key", GroundType.STRING, sourceKey));

    this.dbClient.insert("edge", insertions);

    LOGGER.info("Created edge " + name + ".");
    return new Edge(uniqueId, name, sourceKey, fromNodeId, toNodeId, tags);
  }

  /**
   * Retrieve the DAG leaves for this node.
   *
   * @param sourceKey the key of the node to retrieve leaves for.
   * @return the leaves of the node
   * @throws GroundException an error while retrieving the node
   */
  @Override
  public List<Long> getLeaves(String sourceKey) throws GroundException {
    Edge edge = this.retrieveFromDatabase(sourceKey);
    return super.getLeaves(edge.getId());
  }


  @Override
  public Edge retrieveFromDatabase(String sourceKey) throws GroundException {
    return this.retrieveByPredicate("source_key", sourceKey, GroundType.STRING);
  }

  @Override
  public Edge retrieveFromDatabase(long id) throws GroundException {
    return this.retrieveByPredicate("item_id", id, GroundType.LONG);
  }

  private Edge retrieveByPredicate(String fieldName, Object value, GroundType valueType)
      throws GroundException {

    List<DbDataContainer> predicates = new ArrayList<>();
    predicates.add(new DbDataContainer(fieldName, valueType, value));

    CassandraResults resultSet = this.dbClient.equalitySelect("edge",
        DbClient.SELECT_STAR,
        predicates);
    super.verifyResultSet(resultSet, fieldName, value);


    long id = resultSet.getLong("item_id");
    long fromNodeId = resultSet.getLong("from_node_id");
    long toNodeId = resultSet.getLong("to_node_id");

    String name = resultSet.getString("name");
    String sourceKey = resultSet.getString("source_key");

    Map<String, Tag> tags = super.retrieveItemTags(id);

    LOGGER.info("Retrieved edge " + value + ".");
    return new Edge(id, name, sourceKey, fromNodeId, toNodeId, tags);
  }

  /**
   * Update this edge with a new version.
   *
   * @param itemId the item id of the edge
   * @param childId the id of the new child
   * @param parentIds the ids of any parents of the child
   * @throws GroundException an unexpected error during the update
   */
  @Override
  public void update(long itemId, long childId, List<Long> parentIds) throws GroundException {
    super.update(itemId, childId, parentIds);
    parentIds = parentIds.stream().filter(x -> x != 0).collect(Collectors.toList());

    for (long parentId : parentIds) {
      EdgeVersion currentVersion = this.edgeVersionFactory.retrieveFromDatabase(childId);
      EdgeVersion parentVersion = this.edgeVersionFactory.retrieveFromDatabase(parentId);
      Edge edge = this.retrieveFromDatabase(itemId);

      long fromNodeId = edge.getFromNodeId();
      long toNodeId = edge.getToNodeId();

      long fromEndId = -1;
      long toEndId = -1;

      if (parentVersion.getFromNodeVersionEndId() == -1) {
        // update from end id
        VersionHistoryDag dag = this.versionHistoryDagFactory.retrieveFromDatabase(fromNodeId);
        fromEndId = (long) dag.getParent(currentVersion.getFromNodeVersionStartId()).get(0);
      }

      if (parentVersion.getToNodeVersionEndId() == -1) {
        // update to end id
        VersionHistoryDag dag = this.versionHistoryDagFactory.retrieveFromDatabase(toNodeId);
        toEndId = (long) dag.getParent(currentVersion.getToNodeVersionStartId()).get(0);
      }

      if (fromEndId != -1 || toEndId != -1) {
        this.edgeVersionFactory.updatePreviousVersion(parentId, fromEndId, toEndId);
      }
    }
  }
}
