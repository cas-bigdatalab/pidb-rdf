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

import neo4j.structure.api.Neo4jNode;
import neo4j.structure.api.Neo4jRelationship;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.tinkerpop.gremlin.structure.Direction.*;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public final class Neo4jVertex extends Neo4jElement implements Vertex, WrappedVertex<Neo4jNode> {

    public static final String LABEL_DELIMINATOR = "::";

    public Neo4jVertex(final Neo4jNode node, final Neo4jGraph graph) {
        super(node, graph);
    }

    @Override
    public Edge addEdge(final String label, final Vertex inVertex, final Object... keyValues) {
        if (null == inVertex) throw Graph.Exceptions.argumentCanNotBeNull("inVertex");
        ElementHelper.validateLabel(label);
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        if (ElementHelper.getIdValue(keyValues).isPresent())
            throw Edge.Exceptions.userSuppliedIdsNotSupported();

        this.graph.tx().readWrite();
        final Neo4jNode node = (Neo4jNode) this.baseElement;
        final Neo4jEdge edge = new Neo4jEdge(node.connectTo(((Neo4jVertex) inVertex).getBaseVertex(), label), this.graph);
        ElementHelper.attachProperties(edge, keyValues);
        return edge;
    }

    @Override
    public <V> VertexProperty<V> property(final String key, final V value) {
        return this.property(VertexProperty.Cardinality.single, key, value);
    }

    @Override
    public void remove() {
        this.graph.tx().readWrite();
        this.graph.trait.removeVertex(this);
    }

    @Override
    public <V> VertexProperty<V> property(final VertexProperty.Cardinality cardinality, final String key, final V value, final Object... keyValues) {
        ElementHelper.validateProperty(key, value);
        if (ElementHelper.getIdValue(keyValues).isPresent())
            throw Vertex.Exceptions.userSuppliedIdsNotSupported();
        this.graph.tx().readWrite();
        return this.graph.trait.setVertexProperty(this, cardinality, key, value, keyValues);
    }

    @Override
    public <V> VertexProperty<V> property(final String key) {
        this.graph.tx().readWrite();
        return this.graph.trait.getVertexProperty(this, key);
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        this.graph.tx().readWrite();
        return this.graph.trait.getVertexProperties(this, propertyKeys);
    }

    @Override
    public Neo4jNode getBaseVertex() {
        return (Neo4jNode) this.baseElement;
    }

    @Override
    public String label() {
        this.graph.tx().readWrite();
        return String.join(LABEL_DELIMINATOR, this.labels());
    }

    @Override
    public Iterator<Vertex> vertices(final Direction direction, final String... edgeLabels) {
        this.graph.tx().readWrite();
        return new Iterator<Vertex>() {
            final Iterator<Neo4jRelationship> relationshipIterator = IteratorUtils.filter(0 == edgeLabels.length ?
                    BOTH == direction ?
                            IteratorUtils.concat(getBaseVertex().relationships(Neo4jHelper.mapDirection(OUT)).iterator(),
                                    getBaseVertex().relationships(Neo4jHelper.mapDirection(IN)).iterator()) :
                            getBaseVertex().relationships(Neo4jHelper.mapDirection(direction)).iterator() :
                    BOTH == direction ?
                            IteratorUtils.concat(getBaseVertex().relationships(Neo4jHelper.mapDirection(OUT), (edgeLabels)).iterator(),
                                    getBaseVertex().relationships(Neo4jHelper.mapDirection(IN), (edgeLabels)).iterator()) :
                            getBaseVertex().relationships(Neo4jHelper.mapDirection(direction), (edgeLabels)).iterator(), graph.trait.getRelationshipPredicate());

            @Override
            public boolean hasNext() {
                return this.relationshipIterator.hasNext();
            }

            @Override
            public Neo4jVertex next() {
                return new Neo4jVertex(this.relationshipIterator.next().other(getBaseVertex()), graph);
            }
        };
    }

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        this.graph.tx().readWrite();
        return new Iterator<Edge>() {
            final Iterator<Neo4jRelationship> relationshipIterator = IteratorUtils.filter(0 == edgeLabels.length ?
                    BOTH == direction ?
                            IteratorUtils.concat(getBaseVertex().relationships(Neo4jHelper.mapDirection(OUT)).iterator(),
                                    getBaseVertex().relationships(Neo4jHelper.mapDirection(IN)).iterator()) :
                            getBaseVertex().relationships(Neo4jHelper.mapDirection(direction)).iterator() :
                    BOTH == direction ?
                            IteratorUtils.concat(getBaseVertex().relationships(Neo4jHelper.mapDirection(OUT), (edgeLabels)).iterator(),
                                    getBaseVertex().relationships(Neo4jHelper.mapDirection(IN), (edgeLabels)).iterator()) :
                            getBaseVertex().relationships(Neo4jHelper.mapDirection(direction), (edgeLabels)).iterator(), graph.trait.getRelationshipPredicate());

            @Override
            public boolean hasNext() {
                return this.relationshipIterator.hasNext();
            }

            @Override
            public Neo4jEdge next() {
                return new Neo4jEdge(this.relationshipIterator.next(), graph);
            }
        };
    }

    /////////////// Neo4jVertex Specific Methods for Multi-Label Support ///////////////
    public Set<String> labels() {
        this.graph.tx().readWrite();
        final Set<String> labels = new TreeSet<>(this.getBaseVertex().labels());
        return Collections.unmodifiableSet(labels);
    }

    public void addLabel(final String label) {
        this.graph.tx().readWrite();
        this.getBaseVertex().addLabel(label);
    }

    public void removeLabel(final String label) {
        this.graph.tx().readWrite();
        this.getBaseVertex().removeLabel(label);
    }
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return StringFactory.vertexString(this);
    }

}