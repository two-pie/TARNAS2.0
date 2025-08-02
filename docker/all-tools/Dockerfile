FROM python:3.10-slim

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

# Clone and install tools
RUN git clone https://github.com/tzok/rnapolis-py.git && \
    pip install -r rnapolis-py/requirements.txt && \
    pip install ./rnapolis-py && \
    git clone https://github.com/BGSU-RNA/fr3d-python.git && \
    cd fr3d-python && python -m pip install . && cd .. && \
    pip install mmcif-pdbx && \
    git clone https://github.com/computational-biology/bpnet.git && \
    cp -r bpnet/sysfiles/* /usr/local/bin && \
    git clone https://github.com/srnas/barnaba.git && \
    cd barnaba && pip install -e . && cd .. && \
    git clone https://github.com/rcsb/RNAView.git && \
    cd RNAView && make && cd .. && \
    git clone https://github.com/kad-ecoli/BeEM.git && \
    cd BeEM && \
    g++ -O3 BeEM.cpp -o BeEM

# Set environment variables
ENV NUCLEIC_ACID_DIR=/usr/local/bin
ENV RNAVIEW=/home/RNAView

CMD ["tail", "-f", "/dev/null"]