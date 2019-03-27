package neo4j.api.impl;

import neo4j.api.Neo4jFactory;
import neo4j.api.Neo4jGraphAPI;

import java.util.Map;

public class Neo4jFactoryImpl implements Neo4jFactory {

    @Override
    public Neo4jGraphAPI newGraphDatabase(String path, Map<String, String> config) {
        return null;
    }
}
