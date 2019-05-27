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

import neo4j.structure.api.Neo4jDirection;
import neo4j.structure.api.Neo4jNode;
import org.apache.tinkerpop.gremlin.structure.Direction;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Neo4jHelper {

    private static final String NOT_FOUND_EXCEPTION = "NotFoundException";

    private Neo4jHelper() {
    }

    public static Neo4jDirection mapDirection(final Direction direction) {
        if (direction.equals(Direction.OUT))
            return Neo4jDirection.OUTGOING;
        else if (direction.equals(Direction.IN))
            return Neo4jDirection.INCOMING;
        else
            return Neo4jDirection.BOTH;
    }

    public static boolean isDeleted(final Neo4jNode node) {
        try {
            node.getKeys();
            return false;
        } catch (final RuntimeException e) {
            if (isNotFound(e))
                return true;
            else
                throw e;
        }
    }

    public static boolean isNotFound(final RuntimeException ex) {
        return ex.getClass().getSimpleName().equals(NOT_FOUND_EXCEPTION);
    }

    public static Neo4jNode getVertexPropertyNode(final Neo4jVertexProperty vertexProperty) {
        return vertexProperty.vertexPropertyNode;
    }

    public static void setVertexPropertyNode(final Neo4jVertexProperty vertexProperty, final Neo4jNode node) {
        vertexProperty.vertexPropertyNode = node;
    }
}