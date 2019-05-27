/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package sparql;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.SortCondition;
import org.apache.jena.query.Syntax;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.OpWalker;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.*;

/**
 * The engine that compiles SPARQL to Gremlin traversals thus enabling SPARQL to be executed on any TinkerPop-enabled
 * graph system.
 */
public class SparqlToGremlinCompiler {

    private GraphTraversal<Vertex, ?> traversal;

    private List<Traversal> traversalList = new ArrayList<>();
            List<Traversal> optionalTraversals = new ArrayList<Traversal>();
            List<String> optionalVariable = new ArrayList<String>();
            boolean optionalFlag = false;

    private SparqlToGremlinCompiler(final GraphTraversal<Vertex, ?> traversal) {
        this.traversal = traversal;
    }

    private SparqlToGremlinCompiler(final GraphTraversalSource g) {
        this(g.V());
    }

    /**
     * Converts SPARQL to a Gremlin traversal.
     *
     * @param graph       the {@link Graph} instance to execute the traversal from
     * @param sparqlQuery the query to compile to Gremlin
     */
    public static GraphTraversal<Vertex, ?> compile(final Graph graph, final String sparqlQuery) {
        return compile(graph.traversal(), sparqlQuery);
    }

    /**
     * Converts SPARQL to a Gremlin traversal.
     *
     * @param g           the {@link GraphTraversalSource} instance to execute the traversal from
     * @param sparqlQuery the query to compile to Gremlin
     */
    public static GraphTraversal<Vertex, ?> compile(final GraphTraversalSource g, final String sparqlQuery) {
        return compile(g, QueryFactory.create(Prefixes.prepend(sparqlQuery), Syntax.syntaxSPARQL));
    }

    private GraphTraversal<Vertex, ?> compile(final Query query) {
        final Op op = Algebra.compile(query);
        OpWalker.walk(op, new GremlinOpVisitor());

        int traversalIndex = 0;
        final int numberOfTraversal = traversalList.size();
        final int numberOfOptionalTraversal = optionalTraversals.size();
        Traversal arrayOfAllTraversals[] = null;

        if (numberOfOptionalTraversal > 0) {
            arrayOfAllTraversals = new Traversal[numberOfTraversal - numberOfOptionalTraversal +1];
        } else {
            arrayOfAllTraversals = new Traversal[numberOfTraversal - numberOfOptionalTraversal];
        }
        
        Traversal arrayOfOptionalTraversals[] = new Traversal[numberOfOptionalTraversal];

        for (Traversal tempTrav : traversalList) {
            arrayOfAllTraversals[traversalIndex++] = tempTrav;
        }

        traversalIndex = 0;
        for (Traversal tempTrav : optionalTraversals)
            arrayOfOptionalTraversals[traversalIndex++] = tempTrav;
 
         // creates a map of ordering keys and their ordering direction
        final Map<String, Order> orderingIndex = createOrderIndexFromQuery(query);

        if (traversalList.size() > 0)
            traversal = traversal.match(arrayOfAllTraversals);

        if (optionalTraversals.size() > 0) {
            traversal = traversal.coalesce(__.match(arrayOfOptionalTraversals), (Traversal) __.constant("N/A"));
            for (int i = 0; i < optionalVariable.size(); i++) {
                traversal = traversal.as(optionalVariable.get(i).substring(1));
            }
        }

        final List<String> vars = query.getResultVars();
        if (!query.isQueryResultStar() && !query.hasGroupBy()) {
            final String[] all = new String[vars.size()];
            vars.toArray(all);
            if (query.isDistinct()) {
                traversal = traversal.dedup(all);
            }

            // apply ordering from ORDER BY
            orderingIndex.forEach((k, v) -> traversal = traversal.order().by(__.select(k), v));

            // the result sizes have special handling to get the right signatures of select() called.
            switch (all.length) {
                case 0:
                    throw new IllegalStateException();
                case 1:
                    traversal = traversal.select(all[0]);
                    break;
                case 2:
                    traversal = traversal.select(all[0], all[1]);
                    break;
                default:
                    final String[] others = Arrays.copyOfRange(all, 2, vars.size());
                    traversal = traversal.select(all[0], all[1], others);
                    break;
            }
        }

        if (query.hasGroupBy()) {
            final VarExprList lstExpr = query.getGroupBy();
            String grpVar = "";
            for (Var expr : lstExpr.getVars()) {
                grpVar = expr.getName();
            }

            if (!grpVar.isEmpty())
                traversal = traversal.select(grpVar);
            if (query.hasAggregators()) {
                final List<ExprAggregator> exprAgg = query.getAggregators();
                for (ExprAggregator expr : exprAgg) {
                    if (expr.getAggregator().getName().contains("COUNT")) {
                        if (!query.toString().contains("GROUP")) {
                            if (expr.getAggregator().toString().contains("DISTINCT"))
                                traversal = traversal.dedup(expr.getAggregator().getExprList().get(0).toString().substring(1));
                            else
                                traversal = traversal.select(expr.getAggregator().getExprList().get(0).toString().substring(1));

                            traversal = traversal.count();
                        } else {
                            traversal = traversal.groupCount();
                        }
                    }
                    if (expr.getAggregator().getName().contains("MAX")) {
                        traversal = traversal.max();
                    }
                }
            } else {
                traversal = traversal.group();
            }
        }

        if (query.hasOrderBy() && query.hasGroupBy())
            orderingIndex.forEach((k, v) -> traversal = traversal.order().by(__.select(k), v));

        if (query.hasLimit()) {
            long limit = query.getLimit(), offset = 0;

            if (query.hasOffset())
                offset = query.getOffset();

            if (query.hasGroupBy() && query.hasOrderBy())
                traversal = traversal.range(Scope.local, offset, offset + limit);
            else
                traversal = traversal.range(offset, offset + limit);
        }

        return traversal;
    }

    /**
     * Extracts any {@code SortCondition} instances from the SPARQL query and holds them in an index of their keys
     * where the value is that keys sorting direction.
     */
    private static Map<String, Order> createOrderIndexFromQuery(final Query query) {
        final Map<String, Order> orderingIndex = new HashMap<>();
        if (query.hasOrderBy()) {
            final List<SortCondition> sortingConditions = query.getOrderBy();

            for (SortCondition sortCondition : sortingConditions) {
                final Expr expr = sortCondition.getExpression();

                // by default, the sort will be ascending. getDirection() returns -2 if the DESC/ASC isn't
                // supplied - weird
                orderingIndex.put(expr.getVarName(), sortCondition.getDirection() == -1 ? Order.decr : Order.incr);
            }
        }

        return orderingIndex;
    }

    private static GraphTraversal<Vertex, ?> compile(final GraphTraversalSource g, final Query query) {
        return new SparqlToGremlinCompiler(g).compile(query);
    }

    /**
     * An {@code OpVisitor} implementation that reads SPARQL algebra operations into Gremlin traversals.
     */
    private class GremlinOpVisitor extends OpVisitorBase {

        /**
         * Visiting triple patterns in SPARQL algebra.
         */
        @Override
        public void visit(final OpBGP opBGP) {
        if(optionalFlag)
            {
                opBGP.getPattern().getList().forEach(triple -> optionalTraversals.add(TraversalBuilder.transform(triple)));
                opBGP.getPattern().getList().forEach(triple -> optionalVariable.add(triple.getObject().toString()));
                
            }
            else        
                opBGP.getPattern().getList().forEach(triple -> traversalList.add(TraversalBuilder.transform(triple)));
        }

        /**
         * Visiting filters in SPARQL algebra.
         */
        @Override
        public void visit(final OpFilter opFilter) {
            Traversal traversal;
            for (Expr expr : opFilter.getExprs().getList()) {
                if (expr != null) {
                    traversal = __.where(WhereTraversalBuilder.transform(expr));
                    traversalList.add(traversal);
                }
            }
        }

        
        /**
         * Visiting LeftJoin(Optional) in SPARQL algebra.
         */
        @Override
        public void visit(final OpLeftJoin opLeftJoin) {
            optionalFlag = true;
            optionalVisit(opLeftJoin.getRight());
            if (opLeftJoin.getExprs() != null) {
                for (Expr expr : opLeftJoin.getExprs().getList()) {
                    if (expr != null) {
                        if (optionalFlag)
                            optionalTraversals.add(__.where(WhereTraversalBuilder.transform(expr)));
                    }
                }
            }
        }
        
        /**
         * Walking OP for Optional in SPARQL algebra.
         */
        private void optionalVisit(final Op op) {

            OpWalker.walk(op, this);
        }

        /**
         * Visiting unions in SPARQL algebra.
         */
        @Override
        public void visit(final OpUnion opUnion) {
            final Traversal unionTemp[] = new Traversal[2];
            final Traversal unionTemp1[] = new Traversal[traversalList.size() / 2];
            final Traversal unionTemp2[] = new Traversal[traversalList.size() / 2];

            int count = 0;

            for (int i = 0; i < traversalList.size(); i++) {
                if (i < traversalList.size() / 2)
                    unionTemp1[i] = traversalList.get(i);
                else
                    unionTemp2[count++] = traversalList.get(i);
            }

            unionTemp[1] = __.match(unionTemp2);
            unionTemp[0] = __.match(unionTemp1);

            traversalList.clear();
            traversal = (GraphTraversal<Vertex, ?>) traversal.union(unionTemp);
        }

    }
}
