# pidb-rdf

provides jena API on pidb

```
// some definitions
static String personURI    = "http://somewhere/JohnSmith";
static String fullName     = "John Smith";

// create an empty Model
Model model = PiDBModelFactory.load(new File("/etc/pidb/abc"));

// create the resource
Resource johnSmith = model.createResource(personURI);

// add the property
 johnSmith.addProperty(VCARD.FN, fullName);
 ```
