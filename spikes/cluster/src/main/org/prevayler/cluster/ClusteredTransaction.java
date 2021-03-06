package org.prevayler.cluster;

import org.prevayler.Transaction;
import org.prevayler.cluster.ClusteredPrevayler;

import java.io.Serializable;
import java.util.Date;

class ClusteredTransaction<P extends Serializable> implements Serializable {
  private Transaction<P> transaction;
  private Date executionTime;

  public ClusteredTransaction(Transaction<P> transaction, Date executionTime) {
    this.transaction = transaction;
    this.executionTime = executionTime;
  }

  public Object executeOn(ClusteredPrevayler<P> prevayler) {
    // TODO what about executionTime!
    System.out.println("Executing " + transaction);
    try {
      prevayler.executeBroadcastedTransaction(transaction);
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return null;
  }
}
