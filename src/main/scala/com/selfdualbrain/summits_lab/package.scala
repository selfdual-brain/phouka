package com.selfdualbrain

/**
  * This package contains a sandbox for experimenting with the "abstract casper consensus" component.
  *
  * There is no blockchain here. All we do here is just building abstract j-DAGs and applying the summits theory
  * to them. Means for testing the fault tolerance threshold are established.
  *
  * The code in this package is detached from the rest of Phouka codebase. We only share the abstract consensus implementation.
  */
package object summits_lab {
  type MsgId = Int
  type Vid = Int
}
