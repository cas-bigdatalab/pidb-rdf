package cn.pidb.rdf

import cn.pidb.util.Logging
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.procedure.{Context, Mode, Procedure}

class RDFImport extends Logging {
  @Context
  var graph : GraphDatabaseService = _
  private val DEFAULT_SHORTEN_URLS = true
  private val DEFAULT_TYPES_TO_LABELS = true
  private val DEFAULT_COMMIT_SIZE = 25000
  private val DEFAULT_NODE_CACHE_SIZE = 10000
  val PREFIX_SEPARATOR = "__"

  @Procedure(mode = Mode.WRITE)
  def importRDF () : Unit = {}
}
