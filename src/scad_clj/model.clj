(ns scad-clj.model
  (:use [clojure.core.match :only (match)])
  (:use [clojure.pprint])
  (:use [scad-clj.text :only (text-parts)])
  )

(def pi Math/PI)
(def tau (* 2 pi))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; special variables

(defn fa! [x]
  `(:fa ~x))

(defn fn! [x]
  `(:fn ~x))

(defn fs! [x]
  `(:fs ~x))

(def ^:dynamic *fa* false)
(defmacro with-fa [x & block]
  `(binding [*fa* ~x]
     (list ~@block)))

(def ^:dynamic *fn* false)
(defmacro with-fn [x & block]
  `(binding [*fn* ~x]
     (list ~@block)))

(def ^:dynamic *fs* false)
(defmacro with-fs [x & block]
  `(binding [*fs* ~x]
     (list ~@block)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modifier

(defn modifier [modifier & block]
  (if (some #{modifier} [:# :% :* :!])
    `(:modifier ~(name modifier) ~@block)))

(defn -# [& block] (modifier :# block))
(defn -% [& block] (modifier :% block))
(defn -* [& block] (modifier :* block))
(defn -! [& block] (modifier :! block))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Include & call into Scad libraries

(defn import [file]
  `(:import ~file))

(defn include [library]
  `(:include {:library ~library}))

(defn use [library]
  `(:use {:library ~library}))

(defn libraries [& {uses :use includes :include}]
  (concat
   (map use uses)
   (map include includes)))

(defn call [function & args]
  `(:call {:function ~(name function)} ~args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 2D

(defn square [x y & {:keys [center]}]
  `(:square ~(merge {:x x :y y} (if center {:center center}))))

(defn circle [r]
  `(:circle {:r ~r}))

(defn polygon
  ([points]
     `(:polygon {:points ~points}))
  ([points paths & {:keys [convexity]}]
     `(:polygon {:points ~points :paths ~paths :convexity ~convexity})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 3D

(defn sphere [r & {:keys [center]}]
  (let [args (merge {:r r}
                    (if center [:center center])
                    (if *fa* {:fa *fa*} {})
                    (if *fn* {:fn *fn*} {})
                    (if *fs* {:fs *fs*} {}))]
    `(:sphere ~args)))

(defn cube [x y z & {:keys [center]}]
  `(:cube ~(merge {:x x :y y :z z}
                 (if center {:center center}))))

(defn cylinder [rs h & {:keys [center]}]
  (let [fargs (merge (if center {:center center} {})
                     (if *fa* {:fa *fa*} {})
                     (if *fn* {:fn *fn*} {})
                     (if *fs* {:fs *fs*} {}))]
    (match [rs]
      [[r1 r2]] `(:cylinder ~(merge fargs {:h h :r1 r1 :r2 r2}))
      [r] `(:cylinder ~(merge fargs {:h h :r r})))))

(defn polyhedron
  ([points faces]
    `(:polyhedron {:points ~points :faces ~faces}))
  ([points faces & {:keys [convexity]}]
    `(:polyhedron {:points ~points :faces ~faces :convexity ~convexity})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; transformations

(defn resize[[x y z] & block]
  (let [is-auto (and (keyword? (first block))
                     (= :auto (first block)))
        auto (if is-auto (second block))
        block (if is-auto (rest (rest block)) block)]
    `(:resize {:x ~x :y ~y :z ~z :auto ~auto} ~@block)))

(defn translate [[x y z] & block]
  `(:translate [~x ~y ~z] ~@block))

; multi-arity can't have more than one signature with variable arity. '&'.
(defn rotatev [a [x y z] & block]
  `(:rotatev [~a [~x ~y ~z]] ~@block))

(defn rotatec [[x y z] & block]
  `(:rotatec [~x ~y ~z] ~@block))

(defn rotate [& block]
  (if (number? (first block))
    (rotatev (first block) (second block) (rest (rest block)))
    (rotatec (first block) (rest block))))

(defn scale [[x y z] & block]
  `(:scale [~x ~y ~z] ~@block))

(defn mirror [[x y z] & block]
  `(:mirror [~x ~y ~z] ~@block))

(defn color [[r g b a] & block]
  `(:color [~r ~g ~b ~a] ~@block))

(defn hull [ & block]
  `(:hull  ~@block))

(defn minkowski [ & block]
  `(:minkowski ~@block))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Boolean operations

(defn union [ & block]
  `(:union  ~@block))

(defn intersection [ & block]
  `(:intersection  ~@block))

(defn difference [ & block]
  `(:difference  ~@block))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; other

(defn extrude-linear [{:keys [height twist convexity center]} & block]
  `(:extrude-linear {:height ~height :twist ~twist :convexity ~convexity :center ~center} ~@block))

(defn extrude-rotate
  ([ block ] `(:extrude-rotate {} ~block))
  ([{:keys [convexity]} block] `(:extrude-rotate {:convexity ~convexity} ~block))
  )

(defn projection [cut & block]
  `(:projection {:cut cut} ~@block))

(defn project [& block]
  `(:projection {:cut false} ~@block))

(defn cut [& block]
  `(:projection {:cut true} ~@block))

(defn render [ & block]
  `(:render ~@block))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; text

(defn text [font size text]
  (let [even-odd-paths (text-parts font size text)]
    (:shape
     (reduce (fn [{:keys [union? shape]} paths]
               (if union?
                 {:union? false
                  :shape (apply union shape (map polygon paths))}
                 {:union? true
                  :shape (apply difference shape (map polygon paths))}))
             {:union? true}
             even-odd-paths))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; extended

(defn extrude-curve [{:keys [height radius angle n]} block]
  (let [lim (Math/floor (/ n 2))
        phi (/ (/ angle (- n 1)) 2)]
    (apply union
           (map (fn [x]
                  (let [theta (* 0.5 angle (/ x lim) )
                        r radius
                        dx (* r (- (Math/sin theta)
                                   (* theta (Math/cos theta))))
                        dz (* r (+ (Math/cos theta)
                                   (* theta (Math/sin theta)) (- 1)))]
                    (translate [(+ dx (* 0 (Math/sin theta) (/ height 2)))
                                0
                                (+ dz (* 0 (Math/cos theta) (/ height 2)))]
                      (rotate theta [0 1 0]
                        (intersection
                         (translate [(* r theta) 0 0]
                           (cube (* 2 (+  r height) (Math/sin phi))
                                 1000 (* 2 height)))
                         (extrude-linear {:height height}
                           block))))))
                (range (- lim) (+ lim 1))))))
