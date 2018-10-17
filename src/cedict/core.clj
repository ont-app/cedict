(ns cedict.core
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
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
  "Namespaces and such repended to the output.
  "
  (selmer/render
   "{{prefixes|safe}}
  @base <http://rdf.naturallexicon.org/zh/cedict.ttl>.
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

(defn cedict-uri [ci pinyin]
  "Returns uri for `ci` in cmn (mandarin) namespace.
"
  (shortest-valid-uri (str (prefixes "cedict")
                           ci "-" (str/replace pinyin #" " "-"))))

(defn sense-uri [ci pinyin ordinal]
  "Returns uri for `ci` in cmn (mandarin) namespace.
"
  (shortest-valid-uri (str (prefixes "cedict")
                           ci "-" (str/replace pinyin #" " "-")
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
              ;; this a string with no vowel. An input error, but let it go...
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
    rdfs:subClassOf zh:ChineseWrittenForm;
    rdfs:subClassOf [a owl:restriction;
                     owl:onProperty ontolex:writtenRep;
                     owl:hasValue \"{{hanzi|safe}}\"@{{language-tag}}].
  ")

(def mandarin-template "
{{cmn-uri|safe}} zh:mandarinFormOf {{hanzi-uri-clause|safe}};
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


(defn cedict-ttl [traditional simple pron desc]
  "
Returns a Turtle rendering of the contents of a CEDICT record.
TODO: Consider providing also some clojure-based representation
such as Grafter or EDN-LD, and also JSON-LD.
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
        sense-uri-for (fn [i] (sense-uri simple pinyin (+ i 1)))
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
                      :cedict-uri (cedict-uri simple pinyin)
                      :senseLinks sense-links
                      :senseDeclarations sense-declarations
                      
                     }
        ]

    (str
     (selmer/render hanzi-template template-map)
     (if (not= simple traditional)
       (selmer/render hanzi-template (merge template-map
                                            {:hanzi-uri (hanzi-uri traditional)
                                             :hanzi traditional
                                             :language-tag "zh-Hant"
                                             }))
       "")
     (selmer/render mandarin-template template-map)
     (selmer/render cedict-entry-template template-map)
     )))

(defn translate-cedict-source [instream outstream]
  "
Side-effect: writes a translation of `instream` to `outstream`
Where
<instream> is in cedict format
<oustream> is a ttl translation 
"
  (let [comment-re #"^\s*#.*"
        entry-re #"^(\S+) (\S+) \[([^\]]+)\] (.*)"
        translate-line (fn [line]
                    (if (re-matches comment-re line)
                      ""
                      (if-let [entry (re-matches entry-re line)]
                        (let [[_ traditional simple pron desc] entry]
                          (cedict-ttl
                           traditional simple pron desc)))))
                        

        ]
    
    (doseq [line (line-seq instream)]
      (.write outstream (translate-line line)))))

(def default-source-url
  "This is the last known url for the CEDICT source. Don't expect it to
  move any time soon."
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
        (log/info (str "output to " output-path " successful")))
      
      (catch java.io.FileNotFoundException e
        (throw (Exception.
                (str "Looks like the original source for CEDICT is no longer"
                     " avilable at " input-url ". Consider finding this file's"
                     " new location and defining environment var"
                     " CEDICT_SOURCE_URL.")))))))
                        
