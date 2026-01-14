#!/bin/bash
sbt "runMain CustomDP4SQL.Interface.QueryRunner src/main/resources/university/config.yaml results/university"
