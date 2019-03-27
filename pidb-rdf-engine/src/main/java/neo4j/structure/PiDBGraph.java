package neo4j.structure;

import neo4j.api.Neo4jGraphAPI;
import org.apache.commons.configuration.Configuration;

public class PiDBGraph extends Neo4jGraph {
    protected PiDBGraph(Neo4jGraphAPI baseGraph, Configuration configuration) {
        super(baseGraph, configuration);
    }

    protected PiDBGraph(Configuration configuration) {
        super(configuration);
    }
}
