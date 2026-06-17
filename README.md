# DP4SQL

This repository contains artifacts to reproduce the results presented in the paper 
"DP4SQL: Differentially Private SQL with Flexible Privacy Policies".

## Introduction

DP4SQL is a differentially private SQL system that allows data curators to better customize the plausible deniability requirements for their relational databases. The user only need provide a SQL query, a privacy policy, and a privacy budget for DP4SQL to compute a sufficient amount of noise that must be added to the query answer in order to satisfy $\epsilon$-differential privacy.

![image](architecture.jpg)

## Requirements

Before running, ensure you have the following installed:

- **Java JDK** (11.0.29 recommended)
- **sbt** (1.11.3 recommended)

## How to Reproduce Results

From the root folder, enter the `/results` directory.

```
cd results
```


Reproduce the results from the case study (Section 8).

```
chmod ?
results/university.sh
```

Reproduce the results from the TPC-H evaluation (Section 9).

```
results/tpch.sh
```

The results will be written into the corresponding `university/` and `tpch/` directories.

Alternatively, you can regenerate the results directly with sbt

```
sbt "runMain CustomDP4SQL.Interface.QueryRunner src/main/resources/<schema>/config.yaml results/<schema>"
```
replacing ``<schema>`` with either "university" or "tpch".

The Jupyter Notebook at ``results/plots.ipynb`` was used to generate sensitivity and
error figures.

## How to Reuse Beyond the Paper

This build also supports a playground REPL (Read-Evaluate-Print-Loop) that supports inspecting the university or TPC-H database
schema and sampling Laplace noise for preset queries.

To start, run `repl/university.sh` or `repl/tpch.sh` for the university or TPC-H schema.

Alternatively, you can run the REPL directly with sbt

```
sbt "runMain CustomDP4SQL.Interface.CommandLineInterface src/main/resources/<schema>/config.yaml"
```

replacing ``<schema>`` with either `university` or `tpch`.