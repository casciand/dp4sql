#!/bin/bash
sbt "runMain CustomDP4SQL.Interface.QueryRunner src/main/resources/tpch/config.yaml results/tpch"
