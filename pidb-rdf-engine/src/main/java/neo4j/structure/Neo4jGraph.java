/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package neo4j.structure;

import neo4j.process.traversal.step.sideEffect.CypherStartStep;
import neo4j.process.traversal.strategy.optimization.Neo4jGraphStepStrategy;
import neo4j.process.util.Neo4jCypherIterator;
import neo4j.structure.trait.MultiMetaNeo4jTrait;
import neo4j.structure.trait.Neo4jTrait;
import neo4j.structure.trait.NoMultiNoMetaNeo4jTrait;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.AbstractThreadLocalTransaction;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.TransactionException;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.neo4j.tinkerpop.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Pieter Martin
 */
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_INTEGRATE)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_STANDARD)
@Graph.OptIn("org.apache.tinkerpop.gremlin.neo4j.NativeNeo4jSuite")
public class Neo4jGraph implements Graph, WrappedGraph<Neo4jGraphAPI> {

    public static final Logger LOGGER = LoggerFactory.getLogger(Neo4jGraph.class);

    static {
        TraversalStrategies.GlobalCache.registerStrategies(Neo4jGraph.class, TraversalStrategies.GlobalCache.getStrategies(Graph.class).clone().addStrategies(Neo4jGraphStepStrategy.instance()));
    }

    private static final Configuration EMPTY_CONFIGURATION = new BaseConfiguration() {{
        this.setProperty(Graph.GRAPH, Neo4jGraph.class.getName());
    }};

    protected Features features = new Neo4jGraphFeatures();

    protected Neo4jGraphAPI baseGraph;
    protected BaseConfiguration configuration = new BaseConfiguration();

    public static final String CONFIG_DIRECTORY = "gremlin.neo4j.directory";
    public static final String CONFIG_CONF = "gremlin.neo4j.conf";
    public static final String CONFIG_META_PROPERTIES = "gremlin.neo4j.metaProperties";
    public static final String CONFIG_MULTI_PROPERTIES = "gremlin.neo4j.multiProperties";

    private final Neo4jTransaction neo4jTransaction = new Neo4jTransaction();
    private Neo4jGraphVariables neo4jGraphVariables;

    protected Neo4jTrait trait;

    private void initialize(final Neo4jGraphAPI baseGraph, final Configuration configuration) {
        this.configuration.copy(configuration);
        this.baseGraph = baseGraph;
        this.neo4jGraphVariables = new Neo4jGraphVariables(this);
        this.tx().readWrite();
        final Optional<Boolean> hasMultiProperties = this.neo4jGraphVariables.get(Graph.Hidden.hide(CONFIG_MULTI_PROPERTIES));
        final Optional<Boolean> hasMetaProperties = this.neo4jGraphVariables.get(Graph.Hidden.hide(CONFIG_META_PROPERTIES));
        boolean supportsMetaProperties = hasMetaProperties.orElse(this.configuration.getBoolean(CONFIG_META_PROPERTIES, false));
        boolean supportsMultiProperties = hasMultiProperties.orElse(this.configuration.getBoolean(CONFIG_MULTI_PROPERTIES, false));
        if (supportsMultiProperties != supportsMetaProperties)
            throw new IllegalArgumentException(this.getClass().getSimpleName() + " currently supports either both meta-properties and multi-properties or neither");
        if (!hasMultiProperties.isPresent())
            this.neo4jGraphVariables.set(Graph.Hidden.hide(CONFIG_MULTI_PROPERTIES), supportsMultiProperties);
        if (!hasMetaProperties.isPresent())
            this.neo4jGraphVariables.set(Graph.Hidden.hide(CONFIG_META_PROPERTIES), supportsMetaProperties);
        this.trait = supportsMultiProperties ? MultiMetaNeo4jTrait.instance() : NoMultiNoMetaNeo4jTrait.instance();
        if (supportsMultiProperties)
            LOGGER.warn(this.getClass().getSimpleName() + " multi/meta-properties feature is considered experimental and should not be used in a production setting until this warning is removed");
        this.tx().commit();
    }

    protected Neo4jGraph(final Neo4jGraphAPI baseGraph, final Configuration configuration) {
        this.initialize(baseGraph, configuration);
    }

    protected Neo4jGraph(final Configuration configuration) {
        this.configuration.copy(configuration);
        final String directory = this.configuration.getString(CONFIG_DIRECTORY);
        final Map neo4jSpecificConfig = ConfigurationConverter.getMap(this.configuration.subset(CONFIG_CONF));
        this.baseGraph = Neo4jFactory.Builder.open(directory, neo4jSpecificConfig);
        this.initialize(this.baseGraph, configuration);
    }

    /**
     * Open a new {@link Neo4jGraph} instance.
     *
     * @param configuration the configuration for the instance
     * @return a newly opened {@link org.apache.tinkerpop.gremlin.structure.Graph}
     */
    public static Neo4jGraph open(final Configuration configuration) {
        if (null == configuration) throw Graph.Exceptions.argumentCanNotBeNull("configuration");
        if (!configuration.containsKey(CONFIG_DIRECTORY))
            throw new IllegalArgumentException(String.format("Neo4j configuration requires that the %s be set", CONFIG_DIRECTORY));
        return new Neo4jGraph(configuration);
    }

    /**
     * Construct a Neo4jGraph instance by specifying the directory to create the database in..
     */
    public static Neo4jGraph open(final String directory) {
        final Configuration config = new BaseConfiguration();
        config.setProperty(CONFIG_DIRECTORY, directory);
        return open(config);
    }

    /**
     * Construct a Neo4jGraph instance using an existing Neo4j raw instance.
     */
    public static Neo4jGraph open(final Neo4jGraphAPI baseGraph) {
        return new Neo4jGraph(baseGraph, EMPTY_CONFIGURATION);
    }

    @Override
    public Vertex addVertex(final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        if (ElementHelper.getIdValue(keyValues).isPresent())
            throw Vertex.Exceptions.userSuppliedIdsNotSupported();
        this.tx().readWrite();
        final Neo4jVertex vertex = new Neo4jVertex(this.baseGraph.createNode(ElementHelper.getLabelValue(keyValues).orElse(Vertex.DEFAULT_LABEL).split(Neo4jVertex.LABEL_DELIMINATOR)), this);
        ElementHelper.attachProperties(vertex, keyValues);
        return vertex;
    }

    @Override
    public Iterator<Vertex> vertices(final Object... vertexIds) {
        this.tx().readWrite();
        final Predicate<Neo4jNode> nodePredicate = this.trait.getNodePredicate();
        if (0 == vertexIds.length) {
            return IteratorUtils.stream(this.getBaseGraph().allNodes())
                    .filter(nodePredicate)
                    .map(node -> (Vertex) new Neo4jVertex(node, this)).iterator();
        } else {
            ElementHelper.validateMixedElementIds(Vertex.class, vertexIds);
            return Stream.of(vertexIds)
                    .map(id -> {
                        if (id instanceof Number)
                            return ((Number) id).longValue();
                        else if (id instanceof String)
                            return Long.valueOf(id.toString());
                        else if (id instanceof Vertex) {
                            return (Long) ((Vertex) id).id();
                        } else
                            throw new IllegalArgumentException("Unknown vertex id type: " + id);
                    })
                    .flatMap(id -> {
                        try {
                            return Stream.of(this.baseGraph.getNodeById(id));
                        } catch (final RuntimeException e) {
                            if (Neo4jHelper.isNotFound(e)) return Stream.empty();
                            throw e;
                        }
                    })
                    .filter(nodePredicate)
                    .map(node -> (Vertex) new Neo4jVertex(node, this)).iterator();
        }
    }

    @Override
    public Iterator<Edge> edges(final Object... edgeIds) {
        this.tx().readWrite();
        final Predicate<Neo4jRelationship> relationshipPredicate = this.trait.getRelationshipPredicate();
        if (0 == edgeIds.length) {
            return IteratorUtils.stream(this.getBaseGraph().allRelationships())
                    .filter(relationshipPredicate)
                    .map(relationship -> (Edge) new Neo4jEdge(relationship, this)).iterator();
        } else {
            ElementHelper.validateMixedElementIds(Edge.class, edgeIds);
            return Stream.of(edgeIds)
                    .map(id -> {
                        if (id instanceof Number)
                            return ((Number) id).longValue();
                        else if (id instanceof String)
                            return Long.valueOf(id.toString());
                        else if (id instanceof Edge) {
                            return (Long) ((Edge) id).id();
                        } else
                            throw new IllegalArgumentException("Unknown edge id type: " + id);
                    })
                    .flatMap(id -> {
                        try {
                            return Stream.of(this.baseGraph.getRelationshipById(id));
                        } catch (final RuntimeException e) {
                            if (Neo4jHelper.isNotFound(e)) return Stream.empty();
                            throw e;
                        }
                    })
                    .filter(relationshipPredicate)
                    .map(relationship -> (Edge) new Neo4jEdge(relationship, this)).iterator();
        }
    }

    public Neo4jTrait getTrait() {
        return this.trait;
    }

    @Override
    public <C extends GraphComputer> C compute(final Class<C> graphComputerClass) {
        throw Graph.Exceptions.graphComputerNotSupported();
    }

    @Override
    public GraphComputer compute() {
        throw Graph.Exceptions.graphComputerNotSupported();
    }

    @Override
    public Transaction tx() {
        return this.neo4jTransaction;
    }

    @Override
    public Variables variables() {
        return this.neo4jGraphVariables;
    }

    @Override
    public Configuration configuration() {
        return this.configuration;
    }

    /**
     * This implementation of {@code close} will also close the current transaction on the thread, but it
     * is up to the caller to deal with dangling transactions in other threads prior to calling this method.
     */
    @Override
    public void close() throws Exception {
        this.tx().close();
        if (this.baseGraph != null) this.baseGraph.shutdown();
    }

    public String toString() {
        return StringFactory.graphString(this, baseGraph.toString());
    }

    @Override
    public Features features() {
        return features;
    }

    @Override
    public Neo4jGraphAPI getBaseGraph() {
        return this.baseGraph;
    }

    /**
     * Execute the Cypher query and get the result set as a {@link GraphTraversal}.
     *
     * @param query the Cypher query to execute
     * @return a fluent Gremlin traversal
     */
    public <S, E> GraphTraversal<S, E> cypher(final String query) {
        return cypher(query, Collections.emptyMap());
    }

    /**
     * Execute the Cypher query with provided parameters and get the result set as a {@link GraphTraversal}.
     *
     * @param query      the Cypher query to execute
     * @param parameters the parameters of the Cypher query
     * @return a fluent Gremlin traversal
     */
    public <S, E> GraphTraversal<S, E> cypher(final String query, final Map<String, Object> parameters) {
        this.tx().readWrite();
        final GraphTraversal.Admin<S, E> traversal = new DefaultGraphTraversal<>(this);
        traversal.addStep(new CypherStartStep(traversal, query,
                new Neo4jCypherIterator<>((Iterator) this.baseGraph.execute(query, parameters), this)));
        return traversal;
    }

    class Neo4jTransaction extends AbstractThreadLocalTransaction {

        protected final ThreadLocal<Neo4jTx> threadLocalTx = ThreadLocal.withInitial(() -> null);

        public Neo4jTransaction() {
            super(Neo4jGraph.this);
        }

        @Override
        public void doOpen() {
            threadLocalTx.set(getBaseGraph().tx());
        }

        @Override
        public void doCommit() throws TransactionException {
            try (Neo4jTx tx = threadLocalTx.get()) {
                tx.success();
            } catch (Exception ex) {
                throw new TransactionException(ex);
            } finally {
                threadLocalTx.remove();
            }
        }

        @Override
        public void doRollback() throws TransactionException {
            try (Neo4jTx tx = threadLocalTx.get()) {
                tx.failure();
            } catch (Exception e) {
                throw new TransactionException(e);
            } finally {
                threadLocalTx.remove();
            }
        }

        @Override
        public boolean isOpen() {
            return (threadLocalTx.get() != null);
        }
    }

    public class Neo4jGraphFeatures implements Features {
        protected GraphFeatures graphFeatures = new Neo4jGraphGraphFeatures();
        protected VertexFeatures vertexFeatures = new Neo4jVertexFeatures();
        protected EdgeFeatures edgeFeatures = new Neo4jEdgeFeatures();

        @Override
        public GraphFeatures graph() {
            return graphFeatures;
        }

        @Override
        public VertexFeatures vertex() {
            return vertexFeatures;
        }

        @Override
        public EdgeFeatures edge() {
            return edgeFeatures;
        }

        @Override
        public String toString() {
            return StringFactory.featureString(this);
        }

        public class Neo4jGraphGraphFeatures implements GraphFeatures {

            private VariableFeatures variableFeatures = new Neo4jGraphVariables.Neo4jVariableFeatures();

            Neo4jGraphGraphFeatures() {
            }

            @Override
            public boolean supportsConcurrentAccess() {
                return false;
            }

            @Override
            public boolean supportsComputer() {
                return false;
            }

            @Override
            public VariableFeatures variables() {
                return variableFeatures;
            }

            @Override
            public boolean supportsThreadedTransactions() {
                return false;
            }
        }

        public class Neo4jVertexFeatures extends Neo4jElementFeatures implements VertexFeatures {

            private final VertexPropertyFeatures vertexPropertyFeatures = new Neo4jVertexPropertyFeatures();

            protected Neo4jVertexFeatures() {
            }

            @Override
            public VertexPropertyFeatures properties() {
                return vertexPropertyFeatures;
            }

            @Override
            public boolean supportsMetaProperties() {
                return trait.supportsMetaProperties();
            }

            @Override
            public boolean supportsMultiProperties() {
                return trait.supportsMultiProperties();
            }

            @Override
            public boolean supportsUserSuppliedIds() {
                return false;
            }

            @Override
            public VertexProperty.Cardinality getCardinality(final String key) {
                return trait.getCardinality(key);
            }
        }

        public class Neo4jEdgeFeatures extends Neo4jElementFeatures implements EdgeFeatures {

            private final EdgePropertyFeatures edgePropertyFeatures = new Neo4jEdgePropertyFeatures();

            Neo4jEdgeFeatures() {
            }

            @Override
            public EdgePropertyFeatures properties() {
                return edgePropertyFeatures;
            }
        }

        public class Neo4jElementFeatures implements ElementFeatures {

            Neo4jElementFeatures() {
            }

            @Override
            public boolean supportsUserSuppliedIds() {
                return false;
            }

            @Override
            public boolean supportsStringIds() {
                return false;
            }

            @Override
            public boolean supportsUuidIds() {
                return false;
            }

            @Override
            public boolean supportsAnyIds() {
                return false;
            }

            @Override
            public boolean supportsCustomIds() {
                return false;
            }
        }

        public class Neo4jVertexPropertyFeatures implements VertexPropertyFeatures {

            Neo4jVertexPropertyFeatures() {
            }

            @Override
            public boolean supportsMapValues() {
                return false;
            }

            @Override
            public boolean supportsMixedListValues() {
                return false;
            }

            @Override
            public boolean supportsSerializableValues() {
                return false;
            }

            @Override
            public boolean supportsUniformListValues() {
                return false;
            }

            @Override
            public boolean supportsUserSuppliedIds() {
                return false;
            }

            @Override
            public boolean supportsAnyIds() {
                return false;
            }
        }

        public class Neo4jEdgePropertyFeatures implements EdgePropertyFeatures {

            Neo4jEdgePropertyFeatures() {
            }

            @Override
            public boolean supportsMapValues() {
                return false;
            }

            @Override
            public boolean supportsMixedListValues() {
                return false;
            }

            @Override
            public boolean supportsSerializableValues() {
                return false;
            }

            @Override
            public boolean supportsUniformListValues() {
                return false;
            }
        }
    }
}