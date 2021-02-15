# About this project

## Introduction

**Phouka** is a simulator of a (specific variant of) proof-of-stake blockchain.

The real scope of the simulation is the consensus protocol. We abstract away from the smart contract platform in use.

We use "discrete event simulation" approach so to be able to do exact measurements of blockchain performance, independent of
the host machine performance and any implementation simplifications applied. 

## Goals

Two main use cases are expected:

- educational tool:  as a toy to play with / explain to others / understand the details of how proof-of-stake blockchains
  internally work
- research tool: as an experimentation platform for measuring performance of specific family of consensus protocols and especially
  for investigating the influence of various parameters (network structure and performance, nodes computing power, 
  malicious behaviour, node crashes, network outages etc) on the consensus performance

## Current dev status

This is work in progress now. Beta status is not achieved yet.

COMPLETED:

- DES simulation core engine
- network model
- blockchain simulation engine
- naive casper model
- leaders sequence model
- highway validator model
- statistics processor
- simulation recording
- GUI - mvp framework on top of Java Swing
- GUI - events log browser
- GUI - node stats browser

IN PROGRESS: 

- config serialization / deserialization
- experiments framework
- engine testing / profiling
- GUI - projects manager
- GUI - experiment runner  
- GUI - brickdag graph
- binary release
- user's manual

## How to play with this version

As this is work in progress, there is no binary release yet. Also, the configuration subsystem is not ready yet,
and the documentation of the config file layout is not there. You will need to feel comfortable building the project
from sources. You must also feel comfortable starting the app "manually" by invoking the entry point (i.e. a class
with "main" method). Most likely you would be editing some params directly in code, so from now on I pretty much assume
you are a developer familiar with Scala ecosystem and using some IDE (like IntelliJ Idea).

The entry point to start playing with Phouka is this class:
```
com.selfdualbrain.experiments.Demo1
```

It will run a single simulation, using an example blockchain configuration that you will find hardcoded in this class.
The simulation is configured to run 25 honest validators (no malicious nodes), on a reliable network (no outages).
Network structure, individual per-node download bandwidth and per-node computing power will be randomized. All nodes will
use the same finalizer setup: fault tolerance set to 0.30, and acknowledgement level set to 3. Validators will use
"naive casper" blocks production strategy.

The simulation will stop after processing 1 million events. Upon stopping, the GUI window containing log of events will open.
Additionally, simulation statistics will be printed on System.out.

Feel free to adjust parameters in the Demo1 yourself. You can also use `ReferenceExpConfig` class, which contains a handful
of ready-to-use blockchain configurations.

Caution: `Demo1` instructs the engine to use a different random seed on ever run, so each time you start a simulation, you will
see a different outcome. If needed, you can fix the seed in the `ExperimentConfig.randomSeed` parameter.

## Explanation of the "Phouka" name.

Phouka is a character from Celtic folklore. See [here](https://en.wikipedia.org/wiki/P%C3%BAca).

# Development

## Tech stack

- The development language is Scala.
- The GUI is based on Java Swing.
- The simulation runs totally in RAM (there is no external storage support yet), so it is OutOfMemory exception
  which eventually blows the sim. For reasonably-sized simulations assume that 32 GB of RAM is a good starting point,
  while 16 GB is the absolute minimum.
- DES is implemented in a classic way (priority queue of events), so we do not utilize parallelism. This is for
  a good reason: parallel-DES impacts the complexity of any simulator a lot. Nevertheless, parallel-DES is
  considered as nice-to-have(see the section "Dev roadmap" below).
- The coding style is a proper mix of OO and FP, with OO quite dominating. Partially because we liked it this way, but also
  in many cases this is caused by explicit performance optimizations.

## Dev roadmap

Current roadmap:
- Feb 15, 2021: pre-release (demo version)
- Mar 1st, 2021: engine is feature complete
- Mar 15th, 2021: GUI is feature-complete
- Mar 20th, 2021: binary release ready, entering beta-testing period
- Apr 1st, 2021: User's Manual + demo videos on YouTube
- May 1st, 2021: beta testing complete, official release date of version 1.0
  
Future plans:
- enhanced support for simulation data export (so that external data-science tooling may be plugged-in)
- extracting the engine as a separate library
- Docker support
- storage support (currently the sim runs in RAM)  
- hosting a web version
- full consensus model support (endorsements, eras, validators rotation)  
- enhanced P2P network model (explicit implementation RPS-over-DES, gossip protocol, Kademlia discovery)
- parallel simulation engine (PDES)

# Features (in a nutshell)

_Caution: info below is REALLY concise. Complete Users's Manual will cover the description of the protocol in
great detail. Stay tuned._

## Blockchain consensus model

Phouka is based around "Casper the friendly ghost" line of consensus protocol research, and the especially influencing
paper was [Highway: Efficient Consensus with Flexible Finality](https://arxiv.org/abs/2101.02159). Nevertheless,
the implementation is a rather loose/creative interpretation of the ideas described in paper, plus we use own naming:

- we use blocks and ballots (instead of "units with a block" and "units without a block"); "brick" is a name we use for
  denoting a block or ballot
- bricks form a DAG (brickdag)
- we use more descriptive naming of structures in the brickdag (this includes things like panorama,
  base trimmer, committee, summit, partial summit etc)

The current version of Phouka implements only a sub-protocol of the described solution:

- the set of validators is fixed (= no eras, no validator rotation)
- spam protection aka "endorsements" is not implemented
- all validators share the same finalization params (fault tolerance threshold and ack-level for summits)

Support for the complete protocol is planned in future versions of Phouka.

Actually, Phouka is thought as a platform for playing with a wide collection of protocol variants, especially
in the area of block production strategies. Validator implementations are pluggable. Currently, 3 validator
strategies are implemented:

- Naive Casper - where blocks and ballots are produced randomly, at predefined average frequency
- Simple leaders sequence - round based with fixed round length and pseudo-random leaders sequence;
  only the leader of a round publishes a block, while others produce ballots
- Highway - round-based, with round lengths based on powers-of-two principle; every node picks
  own round exponent and round exponent auto-adjustment algorithm is applied

## Network model

What may be especially interesting from the point of view of research is that the simulation is not only about the
"happy path" of consensus. Actually much of the implementation effort is put into simulation of "bad things" that
interfere with consensus:

- malicious nodes (aka "equivocators")
- network failures
- node crashes

Currently we implement only the "cloning" model of equivocators, so when a validator node splits into N identical, independent validator
nodes, which are not "aware" they are part of split (something like this may happen in real life when a master-slave failover
configuration fails, and the slave node - using same credentials as the master - joins the blockchain network,
using the most up-to-date state snapshot of master).

The network model covers:

- network topology and delays
- per-node download bandwidth
- network outages
- node crashing

To achieve meaningful statistics, we distinguish the binary size of messages in the simulated protocol form
the binary size of messages used in the actual implementaton of Phouka. So for example one can configure the header binary size,
validator id binary size and so on to correctly reflect characteristics of a real system to be simulated.

## Nodes model

Thanks to DES we have the exact simulation of real-time (with microsecond resolution), hence can simulate computing power
of nodes that form the blockchain network. To unify this with blockchain transactions processing model we
use the following units:

- gas - is the unit of on-chain transactions processing cost
- gas/second - is the unit for defining performance of a node
- sprocket = 1 million gas/sec

We simulate nodes as if they were sequential computers (1 processor, single core). For every node its
computing power defined in sprockets is configurable.

A node can become crashed. After a crash, a node is deaf (does not react to incoming messages and also
does not produce any new messages).

# Licensing and forking
This software is covered by GPLv3 license. The text of the license is included in `license.md` file.
In case the GPLv3 licensing model does not fit your purpose, please contact me directly.

I am generally happy for anyone to use Phouka "as is". I am also happy if someone includes Phouka as part of
another GPLv3 project. Everyone is welcome to use Phouka in the context of blockchain research or for  doing blockchain-related
presentations. I will be definitely happy when Phouka will contribute to the blockchain revolution  we are currently witnessing
around the world. Also, if you want to include Phouka as part of some in-house project (so, not distributed), you are free to do so.

For including Phouka in another software, which will be distributed under other name, regardless it
being open source or proprietary, I may or may not be happy depending on a concrete situation, so please
contact me to clarify.