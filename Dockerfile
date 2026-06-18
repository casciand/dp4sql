FROM ubuntu:latest

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install Python, pip, and basic utilities
RUN apt-get update && \
    apt-get install -y python3 python3-pip python3.14-venv openjdk-21-jdk bash curl sudo && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install sbt
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo gpg --dearmor -o /etc/apt/keyrings/scalasbt.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/scalasbt.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    apt-get update && \
    apt-get install -y sbt

# Install Python packages
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install jupyter matplotlib numpy

# Set working directory
WORKDIR /dp4sql

# Copy project files into the container
COPY . .