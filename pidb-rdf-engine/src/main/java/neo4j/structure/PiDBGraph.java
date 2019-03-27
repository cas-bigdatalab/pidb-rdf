package neo4j.structure;

import org.apache.commons.configuration.Configuration;
import org.neo4j.tinkerpop.api.Neo4jGraphAPI;

public class PiDBGraph extends Neo4jGraph {
    protected PiDBGraph(Neo4jGraphAPI baseGraph, Configuration configuration) {
        super(baseGraph, configuration);
    }

    protected PiDBGraph(Configuration configuration) {
        super(configuration);
    }
}
