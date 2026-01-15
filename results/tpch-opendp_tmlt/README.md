# Tumult Analytics Results

## Prerequisites
- Python environment with necessary dependencies
- Java and Spark installed with proper environment variables
- TPC-H dataset (will be downloaded via script)

## Setup

Before running the Jupyter Notebook, you must first prepare the data by running the download script.

### Download Data

Run the following script to download the TPC-H raw data and output data

```
python ./download_data.py
```

### Workflow Overview
1. Download TPC-H raw data using the provided download script
2. Configure JAVA_HOME and SPARK_HOME environment variables in each notebook
3. Execute notebooks in sequence:
    - Transform raw TPC-H data to generate ground truth results
    - Apply customer policy differential privacy protection
    - Apply supplier policy differential privacy protection
    - Evaluate and compare results by calculating absolute and relative errors

### Files and Their Purpose
- `download_data.py` - Automated script to fetch TPC-H data
- `privateSQL_tpch_query_transformation.ipynb` - Data transformation and baseline generation
- `privateSQL_tpch_eval_customer_policy_protected_change_mowner.ipynb` - Customer-level privacy policy evaluation
- `privateSQL_tpch_eval_supplier_policy_protected_change_mowner.ipynb` - Supplier-level privacy policy evaluation
- `privateSQL_tpch_eval_customer_supplier_policy_result.ipynb` - Accuracy metrics computation

### Key Configuration
Each Jupyter notebook requires environment variable updates for local system compatibility before execution.