# pidb-rdf

provides jena API on pidb

```
  // Make a TDB-backed dataset
  String directory = "MyDatabases/Dataset1" ;
  Dataset dataset = PiDBFactory.createDataset(directory) ;
  ...
  dataset.begin(ReadWrite.READ) ;
  // Get model inside the transaction
  Model model = dataset.getDefaultModel() ;
  dataset.end() ;
  ... 
  dataset.begin(ReadWrite.WRITE) ;
  model = dataset.getDefaultModel() ;
  dataset.end() ;
 ```
