# clojure-hbase-schemas

Clojure-HBase-Schemas is a simple library for accessing HBase from
Clojure.  The library was inspired by David Santiago's
[clojure-hbase](http://github.com/davidsantiago/clojure-hbase) and
lifts support for HTable admin functions directly from his library.

Releases are maintained on clojars.  The latest release is:

    com.compasslabs/clojure-hbase-schemas "0.90.4"

## Description

Two main facilities are introduced: schemas and constraints.  Schemas
are type templates that dictate data encoding/decoding for HBase
operations.  Constraints result in method calls on Gets and Scans as
well as passing appropriate sets of filter objects to the Get/Scan
operation.

    (require '[com.compass.hbase.client :as client])
    (require '[com.compass.hbase.filters :as f])

### Schemas

Define a schema for a table called users with two column families,
userinfo and friends. The first seq after the table name is metadata.
:default determines the default data type for qualifiers and values in
any column family not already defined in the schema.  The :row-type is
also defined.  The remainder of the definition consists of qualifier
and value types for each column family.

    (define-schema :users [:defaults [:string :json-key]
    	       	           :row-type :long]
       :userinfo [:keyword :json-key]
       :friends [:long :bool]
       :votes [:keyword :long])

### Client API

Put then Get all values for row ID 100.  The Get procedure looks up a
schema in a global registry (configured by define-schema) for the
table named :users.  Gets and scans return a "family map" for each row
that consists of a dictionary of family names to maps where each
map consists of the keys and values for that family.

    (client/put :users 100 {:userinfo {:name "Test User" :id "21412"} :votes {:up 2 :down 4}})
    (client/get :users 100) => {:userinfo {:name "Test User" :id "21412"} :votes {:up 2 :down 4}}

Increment only returns the modified fields othewise it works the same as put.

    (client/increment :users 100 {:votes {:up 1 :down -2}}) => [100 {:votes {:up 3, :down 2}}]

Additional commands are straightforward

    (client/del :users 100) => fmap
    (client/get-multi :users [100 101 102]) => [fmap fmap fmap]
    (client/put-multi :users [[100 fmap] [200 fmap]])
    (client/scan (fn [id fmap] fmap) :users) => [fmap, fmap, ...]
    (client/do-scan (fn [id fmap] fmap) :users) => [fmap, fmap, ...]
    (client/raw-scan (fn [id fmap] fmap) :users) => [ResultSet, ...]


### Constraints

The Get, Increment and Scan commands above all accept constraint objects which
are used to restrict the rows, families, qualifiers, values and
timestamps returned from a query.  The new API provides a relatively
primitive, but nicely composable mini-language for expressing these
constraints in terms of filters and return value restrictions.
Moreover, it is fairly easy to use constraints in map-reduce job
configuration also.  If you're interested in this in the context of
map-reduce, check out the discussion of [steps and flows in clojure-hadoop](http://ianeslick.com/higher-level-composition-in-clojure-hadoop-st).

Constraints are simply a clojure record that documents the various
constraints you've composed together.  When a Get or Scan command is
executed, the constraints are converted into the specific method calls
or filter objects necessary to satisfy them.

(f/constraints) will create an empty constraint object.

The Constraint protocol supports three methods:

* (project type data)
* (filter type comparison value)
* (page size)

For example, to get users restricted to the :userinfo family

    (client/get :users &lt;id> (-> (f/constraints)
                                (f/project :families [:userinfo])))

To return the userinfo data for all users with a name starting with
"a", the constraint expression is.

    (client/scan (fn [a b] b) :users
   	         (-> (f/constraints)
	             (f/project :families [:userinfo])
	             (f/filter :qualifier [:prefix :<] [:userinfo :name "b"]))

Similar to ClojureQL, constraints can be made and are not materialized until
the get or scan command is actually started, meaning we can store
constraints in vars or have functions that define a set of constraints
and then compose them later.  There are also two convenience functions for
composing these higher order constraint expressions.

    (make-constraints expr1 expr2 ...) and
    (add-constraints constraints expr1 expr2 ...)

So we can now easily define appropriate variables and functions

    (def userinfo (make-constraints
                    (f/project :families [:userinfo])))

    (defn filter-user-name-prefix [c comp prefix]
      (add-constraints c (f/filter :qualifier [:prefix comp] [:userinfo :name prefix])))

And then apply them interactively or programmatically to perform scans.

    (client/scan (fn [a b] b) :users (filter-user-name-prefix userinfo :< "b"))

The currently support projection types include:

* :families - Restrict results to one or more families
* :columns - Restrict row results to a matching family + qualifier
* :row-range - Restrict scan to a range of row values (f/project :row-range [low high])
* :timestamp - Only return values for the given long timestamp
* :timerange - Return values for the given low / high timestamps
* :max-versions - The maximum number of versions of any qualifier+value to return

It is fairly trivial to add new projections or filters; please feel
free to send patches.

Two utility functions make dealing with time ranges easier, (timestamp
ref), (timestamp-now) and (timestamp-ago reference type amount).
timestamp-ago takes a reference timestamp and returns a long value
according to type {:minutes | :hours | :days} and a number.  Arguments
to timestamp and timerange use the timestamp function to interpret
arguments.  This makes it easy then to say things like:

Scan from two days ago until now:

     (f/project constraints :timerange [[:days 2] :now])

Or from 1 month before ref, a long-valued reference timestamp.

     (f/project constraints :timerange [[ref :months 1] ref])

Filter expressions all include a comparison expression.  Typically
you'll use :=, but you can use a variety of comparison types {:binary
| :prefix | :substr | :regex } and the usual boolean comparitors.

Beware that filters don't limit the scan row, so a row filter will
test every row and only return those that pass the test, but if you're
doing a scan operation, this will touch every row in the table which
can take quite a bit of time.

Filter types include:

* (f/filter :row &lt;compare> &lt;value>) - Filter rows by value comparison
* (f/filter :qualifier &lt;compare> [&lt;family> &lt;name>]) - Passes all qualifier names in the given family where (&lt;compare> qualifier &lt;name>) is true
* (f/filter :column &lt;compare> [&lt;family> &lt;qualifier> &lt;value>]) - Pass all columns where the value comparison is true
* (f/filter :cell &lt;compare> [&lt;value> &lt;type>]) - Pass all qualifier-value pairs where the value matches &lt;value>.
* (f/filter :keys-only &lt;ignored>) - Only return the qualifiers, no values
* (f/filter :first-kv-only &lt;ignored> - Only return the first qualifier-value pair (good for getting matching rows without returning much data
* (f/filter :limit &lt;size>) - Only return &lt;size> rows using PageFilter.

There are some compositional semantics missing, such as ignoring rows
where certain columns don't match, rather than filtering just
key-value pairs.  This will be addressed in a later revision.

## License

BSD
