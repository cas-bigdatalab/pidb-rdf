package NeoRDF;

import neo4j.structure.Neo4jGraph;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import sparql.SparqlToGremlinCompiler;

public class Neo4JCommunityTest {
    public static void main(String[] args) {
        String dbPath = "C:\\Users\\coco1\\IdeaProjects\\testNeoRDF\\src\\neo4jDB\\graph.db";
        String sparql = "SELECT * WHERE { }";
        Neo4jGraph graph = Neo4jGraph.open(dbPath);
        GraphTraversal cypherTraversal = graph.cypher("match (p) return p");
        System.out.println(cypherTraversal.count());
        while (cypherTraversal.hasNext()) {
         Object node =  cypherTraversal.next();
            System.out.println(node);
        }

        GraphTraversal<Vertex, ?> sparqlTraversal = SparqlToGremlinCompiler.compile(graph, sparql);
        System.out.println(sparqlTraversal.count());
        while (sparqlTraversal.hasNext()) {
            Object node =  sparqlTraversal.next();
            System.out.println(node);
        }
    }
}
