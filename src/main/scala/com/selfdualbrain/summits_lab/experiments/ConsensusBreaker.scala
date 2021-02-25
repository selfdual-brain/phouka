package com.selfdualbrain.summits_lab.experiments

import scala.util.Random

/**
  * This works in the context of abstract casper consensus.
  *
  * We generate a j-dag of consensus protocol messages - as if this is a j-dag persisted locally by one of validators.
  *
  * The jdag building has 2 phases:
  * 1. We wait for the summit to appear. The summit uses quorum q = w * (0.5 + t), and acknowledge level k.
  * 2. We wait for the first message that "breaks" finality.
  *
  * By "breaking finality" we mean a message that:
  * 1. Can see at least one k-level message of the summit.
  * 2. Votes for consensus value different than the one finalized in the summit.
  *
  * Such a situation fits the assumptions of the finality theorem. Then, if the theorem is correct, for such a situation to appear,
  * the weight of summit members who equivocate must be at least ftt = 2*t*w*(1 - pow(2,k)).
  *
  * At the point where the breaking vote is found, we re-calculate the summit strength, so to obtain most accurate estimation of ftt.
  * We the check the actual weight of equivocators.
  *
  * Overall, this can be seen as a search for finality theorem counterexamples.
  */
class ConsensusBreaker(
                        random: Random,
                        n: Int,//number of validators
                        totalWeight: Long, //total weight of validators
                        t: Long, //this is 't' from the finality theorem
                        k: Int, //ack level required initial summit
                      ) {

  def runExperiment(): Unit = {
    //todo
  }

}
