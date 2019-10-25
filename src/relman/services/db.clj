(ns relman.services.db
  (:require [hugsql.core :as sql]
            [clojure.java.io :as io]))

(sql/def-db-fns (io/resource "sql/services.sql"))


