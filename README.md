# cedict

This is an RDF translation of CEDICT, whose original data is available
from
`https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz`,
and is discussed at [the MDBG website](https://www.mdbg.net/chinese/dictionary?page=cedict).


The supporting schema can be found in [`data/cedict-schema.ttl`](https://github.com/ont-app/cedict/blob/master/data/cedict-schema.ttl). It
aligns to [the Ontolex ontology](https://www.w3.org/2016/05/ontolex/).

The translated turtle file can be downloaded directly from
[`data/cedict_1_0_ts_utf-8_mdbg.ttl.gz`](https://github.com/ont-app/cedict/blob/master/data/cedict_1_0_ts_utf-8_mdbg.ttl.gz).

The DCAT specification is at [`data/cedict-meta.ttl`](https://github.com/ont-app/cedict/blob/master/data/cedict-meta.ttl).

Chinese has a couple of peculiarities. Its written form extends over a
number of dialects with radically different pronunciations. There is
no inflection to speak of, so each form is canonical. Part-of-speech
for any given form is somewhat fluid and context-dependent.

CEDICT's English representations are descriptive. Some additional work
would be necessary to align some subset of these descriptions to
English forms. This file renders these descriptions as 'glosses'.

## Installation

Normally there's nothing to build. The turtle file can be downloaded directly from
[`data/cedict_1_0_ts_utf-8_mdbg.ttl.gz`](https://github.com/ont-app/cedict/blob/master/data/cedict_1_0_ts_utf-8_mdbg.ttl.gz).

On the off chance you want to regenerate the file:

* Clone this repo
* [Install leiningen](https://leiningen.org/#install)
* $ lein run

By default, the input will be taken from
`resources/cedict_1_0_ts_utf-8_mdbg.txt.gz` and output to
`data/cedict_1_0_ts_utf-8_mdbg.ttl.gz`, but these values can be
overridden by setting environment variables `CEDICT_SOURCE_URL` and
`CEDICT_TARGET_PATH` respectively.


## Usage

This is [RDF](https://en.wikipedia.org/wiki/Resource_Description_Framework) data, so typically you'd load it into the RDF store of your choice and access the data through [SPARQL](https://en.wikipedia.org/wiki/SPARQL) queries.


## Example 

(This same data is available in loadable form at [`data/test-sample.tt`](https://github.com/ont-app/cedict/blob/master/data/test-sample.ttl)l)

~~~~
# [prefix declarations]
hanzi:王
    rdfs:subClassOf zh:ChineseForm;
    rdfs:subClassOf [a owl:Restriction;
                     owl:onProperty ontolex:writtenRep;
                     owl:hasValue "王"@zh];
    zh:label "王"@zh.
  
cmn:王-wáng a zh:MandarinForm;
    zh:writtenForm hanzi:王;
    zh:pinyin "wáng"@zh-Latn-pinyin.

cedict:王-wáng a zh:CedictEntry;
    ontolex:lexicalForm cmn:王-wáng;
    ontolex:sense <http://rdf.naturallexicon.org/zh/cedict/王-wáng#1>;.

<http://rdf.naturallexicon.org/zh/cedict/王-wáng#1> a ontolex:LexicalSense;
  en:gloss """surname Wang"""@en;
.

cedict:王-wáng-2 a zh:CedictEntry;
    ontolex:lexicalForm cmn:王-wáng;
    ontolex:sense <http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#1>, <http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#2>, <http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#3>, <http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#4>;.

<http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#1> a ontolex:LexicalSense;
  en:gloss """king or monarch"""@en;
.
<http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#2> a ontolex:LexicalSense;
  en:gloss """best or strongest of its type"""@en;
.
<http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#3> a ontolex:LexicalSense;
  en:gloss """grand"""@en;
.
<http://rdf.naturallexicon.org/zh/cedict/王-wáng-2#4> a ontolex:LexicalSense;
  en:gloss """great"""@en;
.

cmn:王-wàng a zh:MandarinForm;
    zh:writtenForm hanzi:王;
    zh:pinyin "wàng"@zh-Latn-pinyin.

cedict:王-wàng a zh:CedictEntry;
    ontolex:lexicalForm cmn:王-wàng;
    ontolex:sense <http://rdf.naturallexicon.org/zh/cedict/王-wàng#1>, <http://rdf.naturallexicon.org/zh/cedict/王-wàng#2>;.

<http://rdf.naturallexicon.org/zh/cedict/王-wàng#1> a ontolex:LexicalSense;
  en:gloss """to rule"""@en;
.
<http://rdf.naturallexicon.org/zh/cedict/王-wàng#2> a ontolex:LexicalSense;
  en:gloss """to reign over"""@en;
.
~~~~

## License

Copyright © 2018 Eric D. Scott

The CEDICT data is Licenced under
`https://creativecommons.org/licenses/by-sa/3.0/`. Per the conditions
of that license, this RDF representation is licensed under the same
terms. 

The code is released under the Eclipse Public License, same as Clojure.

