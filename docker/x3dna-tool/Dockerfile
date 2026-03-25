FROM --platform=linux/amd64 python:3.10-slim

ENV DEBIAN_FRONTEND=noninteractive

# Set working directory
WORKDIR /home

# Install system dependencies including Ruby first
RUN apt-get update && apt-get install -y \
    git \
    cmake \
    flex \
    bison \
    build-essential \
    python3-dev \
    ruby \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && pip install --upgrade pip

# Copy and extract X3DNA tarball, then run setup
COPY dssr-basic-linux-v2.8.1.zip .

RUN unzip dssr-basic-linux-v2.8.1.zip \
    && chmod +x x3dna-dssr

ENV X3DNA=/home
ENV PATH=$X3DNA:$PATH
# Set environment variables
#ENV X3DNA=/home/x3dna-v2.4
#ENV PATH=/home/x3dna-v2.4/bin:$PATH

CMD ["tail", "-f", "/dev/null"]