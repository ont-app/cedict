(ns cedict.core
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [selmer.parser :as selmer]
   )
  (:import java.util.zip.GZIPInputStream
           org.apache.jena.shared.impl.PrefixMappingImpl
           ;;java.util.zip.GZIPOutputStream
           )
  (:gen-class))

(def prefixes
  {
   "rdf" "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
   "rdfs" "http://www.w3.org/2000/01/rdf-schema#",
   "xsd" "http://www.w3.org/2001/XMLSchema#",
   "owl" "http://www.w3.org/2002/07/owl#",
   "dct" "http://purl.org/dc/terms/",
   "skos" "http://www.w3.org/2004/02/skos/core#",
   "sh" "http://www.w3.org/ns/shacl#"
   "ontolex" "http://www.w3.org/ns/lemon/ontolex#",
   "hanzi" "http://rdf.naturallexicon.org/zh/written/",
   "cmn" "http://rdf.naturallexicon.org/zh/cmn/",
   "cedict" "http://rdf.naturallexicon.org/zh/cedict/",
   "zh" "http://rdf.naturallexicon.org/zh/ont#",
   "en" "http://rdf.naturallexicon.org/en/ont#",
   "natlex" "http://rdf.naturallexicon.org/ont#",
   })


(def shortest-valid-uri
  "A function which maps `uri` to a qname if well-formed and
  otherwise renders it in angle brackets"
  (let [prefix-mapping (PrefixMappingImpl.)
        ]
    (doseq [[prefix uri] prefixes]
      (.setNsPrefix prefix-mapping prefix uri))
    (fn [uri]
      (or (.qnameFor prefix-mapping uri)
          (str "<" uri ">")))))


(defn turtle-prefix
  "Returns a prefix declaration for `pre` and `uri` in turtle format
  where
  <pre> is an RDF prefix
  <uri> is the uri which <pre> abbreviates
  "
  [[pre uri]]
  (selmer/render "@prefix {{pre}}: <{{uri}}>."
                 {:pre pre
                  :uri uri}))

  
(def header
  "Namespaces and such prepended to the output.
  "
  (selmer/render
   "{{prefixes|safe}}
  
   <> a owl:Ontology ;
    owl:imports <cedict-schema.ttl>;
    .

  <> rdfs:comment \"\"\"
  The contents of this file are derived from CEDICT, available from
  available from
    <https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz>.
      
    Licensed under <https://creativecommons.org/licenses/by-sa/3.0/>.

    Per the conditions of said license, this RDF representation is
    licensed under the same terms.
  
  \"\"\";
  .
"
   {"prefixes" (str/join "\n" (map turtle-prefix prefixes))}
   ))

    
(defn hanzi-uri [ci]
  "Returns uri for `ci` in hanzi namespace.
"
  (shortest-valid-uri (str (prefixes "hanzi") ci)))

(defn cmn-uri [ci pinyin]
  "Returns uri for `ci` in cmn (mandarin) namespace.
"
  (shortest-valid-uri (str (prefixes "cmn")
                           ci "-" (str/replace pinyin #" " "-"))))

(defn cedict-long-uri [history ci pinyin]
  "Returns the uri common to both cedict entry and its senses, without 
abbreviation. This allows us to check the history and know whether to add 
an extra variation index when necessary."
  (let [base-uri 
        (str (prefixes "cedict")
             ci "-" (str/replace pinyin #" " "-"))
        ]
    
    (if (contains? history base-uri)
      (str (prefixes "cedict")
           ci "-" (str/replace pinyin #" " "-")
           "-" (inc (get history base-uri)))
      base-uri)))
  

(defn cedict-uri 
  "Returns uri for `ci` and `pinyin` in cedict namespace, possibly adding a variant index per `history`.
"
  [history ci pinyin]
  (shortest-valid-uri (cedict-long-uri history ci pinyin)))

(defn sense-uri [history ci pinyin ordinal]
  "Returns uri for `ci`/`pinyin`/`ordiinal` in cedict namespace, possibly adding a variant index per `history`.
"
  (shortest-valid-uri (str (cedict-long-uri history ci pinyin)
                           "#" ordinal)))

(def pinyin-diacritics
  "Maps vowels to their corresponding tone diacritics"
  {
   \a (vec "āáǎàa")
   \e (vec "ēéěèe")
   \i (vec "īíǐìi")
   \o (vec "ōóǒòo")
   \u (vec "ūúǔùu")
   \ü (vec "ǖǘǚǜü")
   })

(defn normalize-pinyin
  "Returns `pinyin`*, downcased and with diacritics instead of numbers
    to indicate tone.
  Where
  <pinyin> is a token of 1-5 latin characters followed by a number
  1-5.
  "
  [pinyin]
  (let [dcase (str/lower-case pinyin)
        pinyin-re #"([a-z]+)([1-5])"
        m (re-matches pinyin-re dcase)
        ]
    (if m
      (let [[_ letters tone] m
            ]
        (loop [prefix ""
               c (first letters)
               suffix (rest letters)]
          (if-let [d (get pinyin-diacritics c)]
            ;; c is a vowel. Return with proper diacritic
            (str prefix
                 (str (nth d (dec (Integer. tone))))
                 (apply str suffix))
            ;; else c is not a vowel
            (if (empty? suffix)
              ;; this a string with no vowel.
              (str prefix (str c))
              ;; else keep looking for vowel in suffix
              (recur (str prefix (str c))
                     (first suffix)
                     (rest suffix))))))
      ;; else it didn't match pinyin-re return it unchanged
      pinyin)))

(defn handle-quotes [gloss]
  "Returns `gloss`, possibly modified to avoid quotation errors.
"
  (let [prefix (if (= (nth gloss 0) \")
                 "\n"
                 "")
        suffix (if (= (nth gloss (dec (count gloss))) \")
                 "\n"
                 "")
        
        ]
    (str prefix gloss suffix)))

;; Templates for generating Turtle...
(def hanzi-template "
{{hanzi-uri|safe}}
    rdfs:subClassOf zh:ChineseForm;
    rdfs:subClassOf [a owl:Restriction;
                     owl:onProperty ontolex:writtenRep;
                     owl:hasValue \"{{hanzi}}\"@{{language-tag}}];
    zh:label \"{{hanzi}}\"@{{language-tag}}.
  ")

(def mandarin-template "
{{cmn-uri|safe}} a zh:MandarinForm;
    zh:writtenForm {{hanzi-uri-clause|safe}};
    zh:pinyin \"{{pinyin}}\"@zh-Latn-pinyin.
")

(def cedict-entry-template "
{{cedict-uri|safe}} a zh:CedictEntry;
    ontolex:lexicalForm {{cmn-uri|safe}};
    {{senseLinks|safe}}.
{{senseDeclarations|safe}}
")

(def sense-template "
{{sense-uri|safe}} a ontolex:LexicalSense;
  en:gloss \"\"\"{{desc|safe}}\"\"\"@en;
")


(defn- update-tally [acc key]
  "Returns `acc`, incrementing the tally for `key`.
Where
<acc> := {<key> <tally>, ...}
<key> is an (unversioned) URI which may need to be versioned to avoid 
  duplication.
"
  (update-in acc [key] (fn [v] (if v (inc v) 1))))

;; TODO: Consider providing also some clojure-based representation
;;  such as Grafter or EDN-LD, and also JSON-LD.

(defn cedict-ttl [history traditional simple pron desc]
  "
Returns [`history`* <record>], parsed from fields from the original CEDICT source, informed by  `history` to avoid duplication .
Where
<history> := {<uri> <tally>, ...}
<record> is a string of turtle rendering of the contents of a CEDICT record.
<traditional> is a string of Chinese from the 'traditional' column
<simple> is a string of Chinese from the 'simple' column
<pron> is a string of pinyin from the 'pronunciation' column
<desc> is a string of English delimited by '/' for different(ish) senses.
<uri> is a uri already minted for some output in this or previous <record>s
<tallY> is the number of incomning lines that invoked creation of <uri>
"
  (let [
        hanzi-uris (if (= simple traditional)
                     [(hanzi-uri simple)]
                     [(hanzi-uri simple) (hanzi-uri traditional)])
        pinyin (str/join " "
                         (map normalize-pinyin
                              (str/split pron #" ")))
        senses (seq (filter #(not= % "") (str/split desc #"/")))
        sense-reps (map (fn [i gloss] [i gloss])
                        (range (count senses))
                        senses)
        sense-uri-for (fn [i] (sense-uri history simple pinyin (+ i 1)))
        sense-links (str "ontolex:sense "
                         (str/join ", "
                                   (map sense-uri-for
                                        (range (count senses))))

                         ";")
        sense-declaration (fn [i desc]
                            (selmer/render sense-template
                                           {:sense-uri (sense-uri-for i)
                                            :desc (handle-quotes desc)}))
        sense-declarations (str
                            (str/join "." (map sense-declaration
                                               (range (count senses))
                                               senses))
                            ".")
        template-map {
                      :hanzi-uri (hanzi-uri simple)
                      :hanzi simple
                      :hanzi-uri-clause (str/join "," hanzi-uris)
                      :language-tag "zh"
                      :cmn-uri (cmn-uri simple pinyin)
                      :pinyin pinyin
                      :cedict-uri (cedict-uri history simple pinyin)
                      :senseLinks sense-links
                      :senseDeclarations sense-declarations
                      
                      }

        ]

    ;; return [<history> <turtle record>] ...
    [(reduce update-tally history (concat hanzi-uris
                                          [(:cmn-uri template-map)
                                           ;; tally 'zero history' version...
                                           (cedict-long-uri {} simple pinyin)])),
     (str
      ;; skip duplicate simple hanzi...
      (if (not (contains? history (hanzi-uri simple)))
        (selmer/render hanzi-template template-map)
        "")
      ;; skip duplicate traditional hanzi...
      (if (and (not= simple traditional)
               (not (contains? history (hanzi-uri traditional))))
        (selmer/render hanzi-template (merge template-map
                                             {:hanzi-uri (hanzi-uri traditional)
                                              :hanzi traditional
                                              :language-tag "zh-Hant"
                                              }))
        "")
      ;; skip duplicate mandarin ...
      (if (not (contains? history (:cmn-uri template-map)))
        (selmer/render mandarin-template template-map)
        "")
      ;; Each record translates to one cedict entry, with at least one sense...
      (selmer/render cedict-entry-template template-map)
      )]))

(defn translate-cedict-source [instream outstream]
  "
Side-effect: writes a translation of `instream` to `outstream`
Where
<instream> is in cedict format
<oustream> is a ttl translation 
"
  (let [comment-re #"^\s*#.*"
        entry-re #"^(\S+) (\S+) \[([^\]]+)\] (.*)"
        translate-line (fn [history line]
                         (if (re-matches comment-re line)
                           [history ""]
                           (if-let [entry (re-matches entry-re line)]
                             (let [[_ traditional simple pron desc] entry]
                               (cedict-ttl
                                history traditional simple pron desc)))))
                        
        history (atom {})
        ]
    
    (doseq [line (line-seq instream)]
      (let [[_history record] (translate-line @history line)
            ]
        (reset! history _history)
        (.write outstream record)))))

(def default-source-url
  "A copy of the original, kept in resources directory. Can be overriden by env CEDICT_SOURCE_URL."
  (str "resources/" ;;"https://www.mdbg.net/chinese/export/cedict/"
       "cedict_1_0_ts_utf-8_mdbg.txt.gz"))

(def default-target-path
  "The default path for the output. Can be overridden by env CEDICT_TARGET_PATH.
  "
  "data/cedict_1_0_ts_utf-8_mdbg.ttl")

(defn -main
  "Side effect: writes the contents of the cedict source to
   data/cedict_1_0_ts_utf-8_mdbg.ttl.
  Default values may be overridden by env CEDICT_SOURCE_URL and
  CEDICT_TARGET_PATH respectively.
  "
  ;; TODO consider compressing the output
  [& args]
  (let [input-url
        (or (System/getenv "CEDICT_SOURCE_URL")
            default-source-url)
        output-path (or (System/getenv "CEDICT_TARGET_PATH")
                        default-target-path)
        ]
    (io/make-parents output-path) ;; ensure directory exists
    (try
      (with-open [instream
                  (-> input-url
                      io/input-stream
                      GZIPInputStream.
                      io/reader)
                  outstream
                  (io/writer output-path)
                  ]
        (.write outstream header)
        (translate-cedict-source instream outstream)
        (assert (io/as-file output-path))
        (.println *err* (str "output to " output-path " successful")))
      
      (catch java.io.FileNotFoundException e
        (throw (Exception.
                (str "Looks like the original source for CEDICT is no longer"
                     " avilable at " input-url ". Consider finding this file's"
                     " new location and defining environment var"
                     " CEDICT_SOURCE_URL.")))))))
                        
