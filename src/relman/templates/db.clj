(ns relman.templates.db
  (:require [hugsql.core :as sql]
            [clojure.java.io :as io]))

(sql/def-db-fns (io/resource "sql/templates.sql"))


