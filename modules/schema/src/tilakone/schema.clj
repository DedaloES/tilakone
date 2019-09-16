(ns tilakone.schema
  (:require [clojure.string :as str]
            [schema.core :as s :refer [defschema]]
            [tilakone.core :as tk])
  (:import (clojure.lang IFn)))


(def StateName s/Any)
(def TransitionName s/Any)
(def Action s/Any)
(def Matcher s/Any)
(def Guard s/Any)


(def state-actions-and-guards {(s/optional-key :actions) [Action]
                               (s/optional-key :guards)  [Guard]})


(defschema Transition {(s/optional-key :name)    TransitionName
                       (s/optional-key :desc)    s/Str
                       (s/optional-key :to)      StateName
                       :on                       Matcher
                       (s/optional-key :guards)  [Guard]
                       (s/optional-key :actions) [Action]
                       s/Keyword                     s/Any})


(defschema State {:name                   StateName
                  (s/optional-key :desc)  s/Str
                  :transitions            [Transition]
                  (s/optional-key :enter) state-actions-and-guards
                  (s/optional-key :stay)  state-actions-and-guards
                  (s/optional-key :leave) state-actions-and-guards
                  s/Keyword                   s/Any})


(defschema FSM {:states                   [State]
                :state                    StateName
                (s/optional-key :match?)  IFn
                (s/optional-key :guard?)  IFn
                (s/optional-key :action!) IFn
                s/Keyword                     s/Any})


(defn validate-states [states]
  (let [known-state? (->> states
                          (map :name)
                          (set))
        errors       (mapcat (fn [state]
                               (->> state
                                    :transitions
                                    (remove (fn [{:keys [to]}]
                                              (or (nil? to)
                                                  (known-state? to))))
                                    (map (fn [transition]
                                           {:state      state
                                            :transition transition
                                            :message        (format "state [%s] has transition [%s] to unknown state [%s]"
                                                                    (-> state :name)
                                                                    (-> transition :name (or "anonymous"))
                                                                    (-> transition :to))}))))
                             states)]
    (when (seq errors)
      (throw (ex-info (str "unknown target states: " (->> errors
                                                          (map :message)
                                                          (str/join ", ")))
                      {:type   :tilakone.core/error
                       :errors errors}))))
  states)


(def fsm-checker (s/checker FSM))


(defn validate-fsm [fsm]
  (when-let [schema-errors (fsm-checker fsm)]
    (throw (ex-info "FSM does not match schema"
                    {:type          :tilakone.core/error
                     :error         :tilakone.core/schema-error
                     :schema-errors schema-errors
                     :fsm           fsm})))
  (validate-states (:states fsm))
  fsm)
