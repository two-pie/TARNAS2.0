FROM python:3.10-slim

ENV DEBIAN_FRONTEND=noninteractive

# Set working directory
WORKDIR /home

# Install system dependencies including Ruby first
RUN dpkg --add-architecture i386 && apt-get update && apt-get install -y \
    git \
    cmake \
    flex \
    bison \
    build-essential \
    python3-dev \
    ruby \
    wget \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && pip install --upgrade pip

# Clone and install tools
RUN pip install RNApolis &&\
    pip install mmcif-pdbx && \
    git clone https://github.com/rcsb/RNAView.git && \
    cd RNAView && make && cd .. && \
    mkdir MC-Annotate && cd MC-Annotate && wget https://major.iric.ca/mc-tools/releases/MC-Annotate.zip \
    && unzip MC-Annotate.zip && chmod +x MC-Annotate && cd ..


# Set environment variables
ENV NUCLEIC_ACID_DIR=/usr/local/bin
ENV RNAVIEW=/home/RNAView

CMD ["tail", "-f", "/dev/null"]