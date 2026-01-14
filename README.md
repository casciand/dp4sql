# DP4SQL: Differentially Private SQL with Flexible Privacy Policies

This repository contains the implementation of the tool described in the paper 
"DP4SQL: Differentially Private SQL with Flexible Privacy Policies", which is currently under review.

## Requirements

Before running, ensure you have the following installed:

- **Java JDK** (11.0.29 recommended)
- **sbt** (1.11.3 recommended)

## Repository Structure

```
.
├── src/
│   └── main/
├── results/
│   ├── tpch/
│   ├── university/
│   ├── tpch.sh
│   ├── university.sh
│   └── plots.ipynb
└── repl/
    ├── tpch.sh
    └── university.sh
```

## Run

To regenerate the results from the case study (Section 8), run `results/university.sh`.
To regenerate the results from the TPC-H evaluation (Section 9), run `results/tpch.sh`.
The results will be written into the corresponding `university/` and `tpch/` directories.

Alternatively, you can regenerate the results directly with sbt

```
sbt "runMain CustomDP4SQL.Interface.QueryRunner src/main/resources/<schema>/config.yaml results/<schema>"
```
replacing ``<schema>`` with either `university` or `tpch`.

The Jupyter Notebook at ``results/plots.ipynb`` was used to generate sensitivity and
error figures.

### REPL

This build also supports a playground REPL (Read-Evaluate-Print-Loop) that supports inspecting the university or TPC-H database
schema and sampling Laplace noise for preset queries.

To start, run `repl/university.sh` or `repl/tpch.sh` for the university or TPC-H schema.

Alternatively, you can run the REPL directly with sbt

```
sbt "runMain CustomDP4SQL.Interface.CommandLineInterface src/main/resources/<schema>/config.yaml"
```

replacing ``<schema>`` with either `university` or `tpch`.